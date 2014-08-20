<%--
  ~ Copyright 2000-2012 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include.jsp" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>

<jsp:useBean id="cons" class="jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants"/>

<jsp:useBean id="refreshablePath" class="java.lang.String" scope="request"/>
<jsp:useBean id="refreshSnapshotsPath" class="java.lang.String" scope="request"/>
</table>
<script type="text/javascript">
    BS = BS || {};
    BS.Clouds = BS.Clouds || {};
    BS.Clouds.VMWareVSphere = BS.Clouds.VMWareVSphere || {
        refreshOptionsUrl: '<c:url value="${refreshablePath}"/>',
        refreshSnapshotsUrl: '<c:url value="${refreshSnapshotsPath}"/>',
        optionsFetched: false,
        _dataKeys: [ 'vmName', 'snapshotName', 'cloneFolder', 'resourcePool', 'cloneBehaviour', 'maxInstances'],
        selectors: {
            imagesSelect: '#image',
            cloneBehaviourRadio: ".cloneBehaviourRadio",
            cloneOptionsRow: '.cloneOptionsRow',
            rmImageLink: '.removeVmImageLink',
            editImageLink: '.editVmImageLink',
            imagesTableRow: '.imagesTableRow'
        },
        init: function () {
            this.$fetchOptionsButton = $j('#vmwareFetchOptionsButton');
            this.$emptyImagesListMessage = $j('.emptyImagesListMessage');
            this.$imagesTableWrapper = $j('.imagesTableWrapper');
            this.$imagesTable = $j("#vmwareImagesTable");
            this.$imagesDataElem = $j('#${cons.imagesData}');
            this.$options = $j('.vmWareSphereOptions');
            this.$image = $j(this.selectors.imagesSelect);
            this.$snapshot = $j('#snapshot');
            this.$cloneFolder = $j("#cloneFolder");
            this.$resourcePool = $j("#resourcePool");
            this.$maxInstances = $j("#maxInstances");
            this.$dialogSubmitButton = $j('#vmwareAddImageButton');
            this.$fetchOptionsError = $j("#error_fetch_options");
            this.$cancelButton = $j('#vmwareCancelAddImageButton');
            this.$showDialogButton = $j('#vmwareShowDialogButton');
            this.loaders = {
                fetchOptions: $j('.fetchingServerOptions'),
                fetchSnapshots: $j('.fetchingSnapshots')
            };

            this.selectors.activeCloneBehaviour = this.selectors.cloneBehaviourRadio + ':checked';

            this._lastImageId = this._imagesDataLength = 0;
            this._toggleDialogShowButton();
            this._initImagesData();
            this._initTemplates();
            this._bindHandlers();
            this.fetchOptions();
            this.renderImagesTable();
        },
        _fetchOptionsInProgress: function () {
            return this.fetchOptionsDeferred ?
                this.fetchOptionsDeferred.state() === 'pending' :
                false;
        },
        fetchOptions: function () {
            var $loader = $j(BS.loadingIcon).clone();

            if ( this._fetchOptionsInProgress() || !this.validateServerSettings()) {
                return false;
            }

            this.fetchOptionsDeferred = $j.Deferred()
                .fail(function (errorText) {
                    this.addError("Unable to fetch options: " + errorText);
                    BS.VMWareImageDialog.close();
                }.bind(this));

            this._toggleFetchOptionsButton();
            this._toggleDialogSubmitButton();
            this._toggleLoadingMessage('fetchOptions', true);
            $loader.insertAfter(this.$fetchOptionsButton);

            BS.ajaxRequest(this.refreshOptionsUrl, {
                parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
                onComplete: function () {
                    $loader.remove();
                    this._toggleFetchOptionsButton(true);
                    this._toggleLoadingMessage('fetchOptions');
                }.bind(this),
                onFailure: function (response) {
                    this.fetchOptionsDeferred.reject(response.getStatusText());

                }.bind(this),
                onSuccess: function (response) {
                    var $response = $j(response.responseXML),
                        $errors = $response.find("errors:eq(0) error"),
                        $vms = $response.find('VirtualMachines:eq(0) VirtualMachine'),
                        $pools = $response.find('ResourcePools:eq(0) ResourcePool'),
                        $folders = $response.find('Folders:eq(0) Folder');

                    if ($errors.length) {
                        this.fetchOptionsDeferred.reject($errors.text());
                    } else if ($vms.length) {
                        this.optionsFetched = true;
                        this.fillOptions($vms, $pools, $folders);
                        this._toggleDialogSubmitButton(true);
                        this._toggleDialogShowButton(true);
                        this.fetchOptionsDeferred.resolve();
                    }
                }.bind(this)
            });

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        renderImagesTable: function () {
            if (this._imagesDataLength) {
                Object.keys(this.imagesData).forEach(function (imageId) {
                    this._renderImageRow(this.imagesData[imageId], imageId);
                }.bind(this));
            }
            this._toggleImagesTable();
        },
        fillOptions: function ($vms, $pools, $folders) {
            this
                ._displayImagesSelect($vms)
                ._displayPoolsSelect($pools)
                ._displayFoldersSelect($folders);
        },
        /**
         * Validates server URL and displays error if URL seems to be incorrect
         */
        validateServerSettings: function () {
            var url = $j("#${cons.serverUrl}").val(),
                isValid = (/^https:\/\/.*\/sdk$/).test(url);

            this.clearErrors();

            if (url.length && !isValid) {
                this.addError("Server URL doesn't seem to be correct. <br/>" +
                "Correct URL should look like this: <strong>https://vcenter/sdk</strong>");
            }

            return isValid;
        },
        validateOptions: function () {
            var maxInstances = this.$maxInstances.val(),
                isValid = true;

            // checking properties
            this.clearOptionsErrors(['image', 'instances']);

            if ( ! this.$image.val()) {
                this.addOptionError("Please select a VM", "image");
                isValid = false;
            }

            if (maxInstances != '' && ! $j.isNumeric(maxInstances)) {
                this.addOptionError("Must be number", "instances");
                isValid = false;
            }

            if (this._isClone()) {
                if (!this.$cloneFolder.val()) {
                    this.addOptionError("Please select folder", "folder");
                    isValid = false;
                }
                if (!this.$resourcePool.val()) {
                    this.addOptionError("Please select pool", "pool");
                    isValid = false;
                }
            }

            return isValid;
        },
        addImage: function () {
            var newImageId = this._lastImageId++,
                newImage = this._collectImageData();

            this._renderImageRow(newImage, newImageId);
            this.imagesData[newImageId] = newImage;
            this._imagesDataLength += 1;
            this.saveImagesData();
            this._toggleImagesTable();

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        /**
         * gathers image data from dialog markup
         */
        _collectImageData: function () {
            return  {
                vmName: this.$image.val(),
                snapshotName: this._visibleValue(this.$snapshot) || '[Latest Version]',
                cloneFolder: this._visibleValue(this.$cloneFolder),
                resourcePool: this._visibleValue(this.$resourcePool),
                cloneBehaviour: $j(this.selectors.activeCloneBehaviour).val(),
                maxInstances: this._visibleValue(this.$maxInstances) || '1'
            };
        },
        removeImage: function ($elem) {
            delete this.imagesData[$elem.data('imageId')];
            this._imagesDataLength -= 1;
            $elem.parents(this.selectors.imagesTableRow).remove();
            this.saveImagesData();
            this._toggleImagesTable();
        },
        editImage: function (id) {
            this.imagesData[id] = this._collectImageData();
            this.saveImagesData();
            this.$imagesTable.find(this.selectors.imagesTableRow).remove();
            this.renderImagesTable();
        },
        showEditDialog: function ($elem) {
            var imageId = $elem.data('imageId');

            this.showDialog('edit', imageId);

            this.fetchOptionsDeferred.then(function () {
                var image = this.imagesData[imageId];

                this.$options.find(this.selectors.cloneBehaviourRadio).trigger('change', image.cloneBehaviour);
                this.$cloneFolder.trigger('change', image.cloneFolder);
                this.$resourcePool.trigger('change', image.resourcePool);
                this.$maxInstances.trigger('change', image.maxInstances);

                this.$image.trigger('change', image.vmName);
                this.fetchSnapshotsDeferred
                    .then(function () {
                        this.$snapshot.trigger('change', image.snapshotName === '[Latest Version]' ? '' : image.snapshotName);
                    }.bind(this));
            }.bind(this));

        },
        showDialog: function (action, imageId) {
            action = action ? 'Edit' : 'Add';
            this.clearOptionsErrors();
            $j('#VMWareImageDialogTitle').text(action + ' Image');

            this.$dialogSubmitButton.val(action).data('imageId', imageId);

            BS.VMWareImageDialog.showCentered();
        },
        saveImagesData: function () {
            var data = Object.keys(this.imagesData).map(function (imageId) {
                return this._dataKeys.map(function (key) {
                    return this.imagesData[imageId][key];
                }.bind(this)).join(';').replace(/\[Latest version]/g, '');
            }.bind(this)).join(';X;:');

            if (data.length) {
                data+= ';X;:';
            }

            this.$imagesDataElem.val(data);
        },
        _fetchSnapshotsInProgress: function () {
            return this.fetchSnapshotsDeferred ?
            this.fetchSnapshotsDeferred.state() === 'pending' :
                    false;
        },
        /**
         * fetches snapshot list for selected image
         */
        fetchSnapshots: function () {
            if (this._fetchSnapshotsInProgress()) {
                return false;
            }
            this.fetchSnapshotsDeferred = $j.Deferred();
            $j('#realImageInput').val(this.$image.val());
            this._toggleLoadingMessage('fetchSnapshots', true);
            if (this._isClone()) {
                this._toggleDialogSubmitButton();
            }
            BS.ajaxRequest(this.refreshSnapshotsUrl, {
                parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
                onFailure: function (response) {
                    console.error('something went wrong', response);
                    this.fetchSnapshotsDeferred.reject();
                },
                onSuccess: function (response) {
                    var $response = $j(response.responseXML);

                    this._toggleDialogSubmitButton(true);

                    if ($response.length) {
                        this._displaySnapshotSelect($response.find('Snapshots:eq(0) Snapshot'));
                    }
                    this._toggleLoadingMessage('fetchSnapshots');

                    this.fetchSnapshotsDeferred.resolve();
                }.bind(this)
            });
        },
        clearErrors: function (target) {
            (target || this.$fetchOptionsError).empty();
        },
        addError: function (errorHTML, target) {
            (target || this.$fetchOptionsError)
                    .append($j("<div>").html(errorHTML));
        },
        addOptionError: function (errorHTML, optionName) {
          this.addError(errorHTML, $j('.option-error_' + optionName));
        },
        /**
         * @param {string[]} [options]
         */
        clearOptionsErrors: function (options) {
          options || [ 'image', 'snapshot', 'folder', 'pool', 'instances'].forEach(function (optionName) {
              this.clearErrors($j('.option-error_' + optionName));
          }.bind(this));
        },
        _initImagesData: function () {
            var self = this,
                rawImagesData = this.$imagesDataElem.val() || '',
                imagesData = rawImagesData && rawImagesData.split(';X;:') || [];

            this.imagesData = imagesData.reduce(function (accumulator, imageDataStr) {
                var props = imageDataStr.split(';'),
                    id;

                // drop images without vmName
                if (props[0].length) {
                    id = self._lastImageId++;
                    accumulator[id] = {};
                    self._dataKeys.forEach(function(key, index) {
                        accumulator[id][key] = props[index];
                    });
                    self._imagesDataLength++;
                }
                return accumulator;
            }, {});
        },
        _bindHandlers: function () {
            var self = this;

            //// Click Handlers
            this.$fetchOptionsButton.on('click', this._fetchOptionsClickHandler.bind(this));
            this.$showDialogButton.on('click', function () {
                if (! this.$showDialogButton.attr('disabled')) {
                    this.showDialog();
                }
                return false;
            }.bind(this));
            this.$dialogSubmitButton.on('click', this._submitDialogClickHandler.bind(this));
            this.$cancelButton.on('click', function () {
                BS.VMWareImageDialog.close();
                return false;
            });
            this.$imagesTable.on('click', this.selectors.rmImageLink, function () {
                var $this = $j(this),
                        id = $this.data('imageId'),
                        name = BS.Clouds.VMWareVSphere.imagesData[id].vmName;

                if (confirm('Are you sure you want to remove the image "' + name + '"?')) {
                    self.removeImage($this);
                }
                return false;
            });
            this.$imagesTable.on('click', this.selectors.editImageLink, function () {
                self.showEditDialog($j(this));

                return false;
            });

            //// Change Handlers
            // - clone behaviour
            $j(this.selectors.cloneBehaviourRadio).on('change', function (e, val) {
                var $elementsToToggle = $j(this.selectors.cloneOptionsRow);

                if (typeof val !== 'undefined') {
                    $j(this.selectors.cloneBehaviourRadio + '[value="' + val + '"]').prop('checked', true);
                }

                $elementsToToggle.toggle(this._isClone());
            }.bind(this));
            // - image
            this.$options.on('change', this.selectors.imagesSelect, function(e, value) {
                this._validateSelectChange(this.$image, value);

                this.fetchSnapshots();
            }.bind(this));
            // - snapshot
            // - folder
            // - pool
            this.$snapshot.add(this.$cloneFolder).add(this.$resourcePool).on('change', function (e, val) {
                this._validateSelectChange($j(e.target), val);
            }.bind(this));

            this.$image.add(this.$snapshot).on('change', this.validateOptions.bind(this));

            // - instances
            this.$maxInstances.on('change', function (e, val) {
                $j(this).val(val);
            });
        },
        _validateSelectChange: function ($elem, value) {
            var errId = $elem.attr('data-err-id');

            if (typeof value !== 'undefined') {
                if (! $elem.find('option[value="' + value + '"]').length) {
                    this.addOptionError("The " + errId + " '" + value + "' does not exist", errId);
                } else {
                    $elem.val(value);
                }
            }
        },
        _fetchOptionsClickHandler: function () {
            if (this.$fetchOptionsButton.attr('disabled') !== 'true') { // it may be undefined
                this.fetchOptions();
            }

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        _submitDialogClickHandler: function() {
            if (this.validateOptions()) {
                if (this.$dialogSubmitButton.val().toLowerCase() === 'edit') {
                    this.editImage(this.$dialogSubmitButton.data('imageId'));
                } else {
                    this.addImage();
                }

                BS.VMWareImageDialog.close();
            }

            return false;
        },
        _renderImageRow: function (rows, id) {
            var $row = $j(this.templates.imagesTableRow).clone();

            this._dataKeys.forEach(function (className) {
                $row.find('.' + className).text(rows[className]);
            });
            $row.find(this.selectors.rmImageLink).data('imageId', id);
            $row.find(this.selectors.editImageLink).data('imageId', id);
            this.$imagesTable.append($row);
        },
        _toggleImagesTable: function () {
            var toggle = !!this._imagesDataLength;
            this.$imagesTableWrapper.show();
            this.$emptyImagesListMessage.toggle(!toggle);
            this.$imagesTable.toggle(toggle);
        },
        _displayImagesSelect: function ($vms) {
            var self = this,
                $select = $j(this.templates.imagesSelect).clone(),
                $vmGroup = $select.find(".vmGroup"),
                $templatesGroup = $select.find(".templatesGroup");

            $vms.each(function () {
                self._appendOption($j(this).attr('template') == 'false' ? $vmGroup : $templatesGroup, $j(this).attr('name'));
            });

            this.$image.replaceWith($select);
            this.$image = $select;

            return this;
        },
        _displayPoolsSelect: function ($pools) {
            var self = this;

            this.$resourcePool.children().remove();
            this._appendOption(this.$resourcePool, '', '--Please select pool--');
            $pools.each(function () {
                self._appendOption(self.$resourcePool, $j(this).attr('name'));
            });

            return this;
        },
        _displayFoldersSelect: function ($folders) {
            var self = this;

            this.$cloneFolder.children().remove();
            this._appendOption(this.$cloneFolder, '', '--Please select folder--');
            $folders.each(function () {
                self._appendOption(self.$cloneFolder, $j(this).attr('name'));
            });

            return this;
        },
        _displaySnapshotSelect: function ($snapshots) {
            var self = this;

            this.$snapshot
                .children().remove().end()
                .append("<option value=''>[Latest version]</option>");

            $snapshots.each(function () {
                self._appendOption(self.$snapshot, $j(this).attr("name"));
            });
        },
        _isClone: function () {
            return $j(this.selectors.activeCloneBehaviour).val() !== 'START';
        },
        _toggleDialogSubmitButton: function (enable) {
            this.$dialogSubmitButton.prop('disabled', !enable);
        },
        _toggleFetchOptionsButton: function (enable) {
            this.$fetchOptionsButton.prop('disabled', !enable);
        },
        _toggleDialogShowButton: function (enable) {
            this.$showDialogButton.attr('disabled', !enable);
        },
        _toggleLoadingMessage: function (loaderName, show) {
            this.loaders[loaderName][show ? 'removeClass' : 'addClass']('message_hidden');
        },
        _visibleValue: function (elem) {
            return elem.is(":visible") ? elem.val() : '';
        },
        _appendOption: function ($target, value, text) {
            $target.append($j('<option>').attr('value', value).text(text || value));
        },
        _initTemplates: function() {
            // Prototype.js ignores script type when parsing scripts (for refresable),
            // so custom script types do not work.
            // Older IE try to interpret `template` tags, that approach fails too.
            this.templates = {
                imagesTableRow: $j('<tr class="imagesTableRow">\
<td class="vmName"></td>\
<td class="snapshotName"></td>\
<td class="cloneFolder"></td>\
<td class="resourcePool"></td>\
<td class="cloneBehaviour"></td>\
<td class="maxInstances"></td>\
<td><a href="#" class="editVmImageLink">edit</a></td>\
<td><a href="#" class="removeVmImageLink">delete</a></td>\
            </tr>'),
                imagesSelect: $j('<select name="prop:_image" id="image" data-err-id="image">\
    <option value="">--Please select a VM--</option>\
    <optgroup label="Virtual machines" class="vmGroup"></optgroup>\
    <optgroup label="Templates" class="templatesGroup"></optgroup>\
</select>')
            }
        }
    };
</script>
<style>
    #VMWareImageDialog .message-wrapper {
        position: absolute;
        top: 48px;
        width: 100%;
    }
    #VMWareImageDialog .message {
        opacity: 1;
    }

    #VMWareImageDialog .message_hidden {
        display: none;
        opacity: 0;
    }

    #VMWareImageDialog .message .icon-refresh {
        margin-right: 6px;
    }

    #VMWareImageDialog .runnerFormTable th {
        padding-left: 0;
    }

    #VMWareImageDialog .modalDialogBody {
        padding-top: 20px;
    }
</style>

<table class="runnerFormTable">
  <tr>
    <th><label for="${cons.serverUrl}">Server URL: <l:star/></label></th>
    <td><props:textProperty name="${cons.serverUrl}" className="longField"/></td>
  </tr>

  <tr>
    <th><label for="${cons.username}">Username: <l:star/></label></th>
    <td><props:textProperty name="${cons.username}"/></td>
  </tr>

  <tr>
    <td><label for="secure:${cons.password}">Password: <l:star/></label></td>
    <td><props:passwordProperty name="secure:${cons.password}"/></td>
  </tr>
  <tr>
    <td colspan="2">
      <span id="error_fetch_options" class="error"></span>
      <div>
        <forms:button id="vmwareFetchOptionsButton">Fetch options</forms:button>
      </div>
    </td>
  </tr>

<tr class="imagesTableWrapper hidden">
  <td colspan="2">
    <span class="emptyImagesListMessage hidden">You haven't added any images yet.</span>
    <table id="vmwareImagesTable" class="imagesTable runnerFormTable hidden">
      <tbody>
      <tr>
        <th>Image name</th>
        <th>Snapshot</th>
        <th>Clone folder</th>
        <th>Resource pool</th>
        <th>Start behaviour</th>
        <th>Max number of instances</th>
        <th></th>
        <th></th>
      </tr>
      </tbody>
    </table>
    <props:hiddenProperty name="${cons.imagesData}"/>
  </td>
</tr>

    <tr>
        <td colspan="2">
            <forms:addButton title="Add image" id="vmwareShowDialogButton">Add image</forms:addButton>
        </td>
    </tr>
</table>
<input type="hidden" name="prop:image" id="realImageInput"/>
<bs:dialog dialogId="VMWareImageDialog" title="Add Image" closeCommand="BS.VMWareImageDialog.close()"
           dialogClass="VMWareImageDialog vmWareSphereOptions" titleId="VMWareImageDialogTitle"
        ><div class="message-wrapper"><div class="fetchingServerOptions message message_hidden"><i class="icon-refresh icon-spin"></i>Fetching server options...</div>
        <div class="fetchingSnapshots message message_hidden"><i class="icon-refresh icon-spin"></i>Fetching snapshots...</div>
    </div>
    <table class="runnerFormTable">
            <tr>
                <th>Select an image type:</th>
                <td>
                    <input type="radio" id="cloneBehaviour_START" name="cloneBehaviour" value="START" checked="true" class="cloneBehaviourRadio"/>
                    <label for="cloneBehaviour_START">Start/Stop instance</label>
                    <br/>
                    <input type="radio" id="cloneBehaviour_CLONE" name="cloneBehaviour" value="CLONE" class="cloneBehaviourRadio"/>
                    <label for="cloneBehaviour_CLONE">Fresh clone</label>
                    <br/>
                    <input type="radio" id="cloneBehaviour_ON_DEMAND_CLONE" name="cloneBehaviour" value="ON_DEMAND_CLONE" class="cloneBehaviourRadio"/>
                    <label for="cloneBehaviour_ON_DEMAND_CLONE">On demand clone</label>
                    <br/>
                </td>
            </tr>


            <tr>
                <th><label for="image">Agent image:</label></th>
                <td>
                    <div>
                        <select name="_image" id="image" data-err-id="image"></select>
                    </div>
                    <span class="error option-error option-error_image"></span>
                </td>
            </tr>

            <tr class="hidden cloneOptionsRow"  id="tr_snapshot_name">
                <th><label for="snapshot">Snapshot name:</label></th>
                <td>
                    <select id="snapshot" data-err-id="snapshot"></select>
                    <span class="error option-error option-error_snapshot"></span>
                </td>
            </tr>


            <tr class="hidden cloneOptionsRow">
                <th>
                    <label for="cloneFolder">Folder for clones</label>
                </th>
                <td>
                    <select id="cloneFolder" data-err-id="folder"></select>
                    <span class="error option-error option-error_folder"></span>
                </td>
            </tr>

            <tr class="hidden cloneOptionsRow">
                <th>
                    <label for="resourcePool">Resource pool</label>
                </th>
                <td>
                    <select id="resourcePool" data-err-id="pool"> </select>
                    <span class="error option-error option-error_pool"></span>
                </td>
            </tr>
            <tr class="hidden cloneOptionsRow">
                <th>
                    <label for="maxInstances">Max number of instances</label>
                </th>
                <td>
                    <div>
                        <input type="text" id="maxInstances" value="0"/>
                    </div>
                    <span class="error option-error option-error_instances"></span>
                </td>
            </tr>
        </table>
    <div class="popupSaveButtonsBlock">
        <forms:submit label="Add" type="button" id="vmwareAddImageButton"/>
        <forms:button title="Cancel" id="vmwareCancelAddImageButton">Cancel</forms:button>
    </div>
</bs:dialog>

<script type="text/javascript">
    BS.VMWareImageDialog = OO.extend(BS.AbstractModalDialog, {
        getContainer: function() {
            return $('VMWareImageDialog');
        }
    });
    BS.Clouds.VMWareVSphere.init();
</script>
<table class="runnerFormTable">