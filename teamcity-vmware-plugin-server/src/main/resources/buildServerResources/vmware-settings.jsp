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

<script type="text/javascript">
    BS = BS || {};
    BS.Clouds = BS.Clouds || {};
    BS.Clouds.VMWareVSphere = BS.Clouds.VMWareVSphere || {
        refreshOptionsUrl: '<c:url value="${refreshablePath}"/>',
        refreshSnapshotsUrl: '<c:url value="${refreshSnapshotsPath}"/>',
        _dataKeys: [ 'vmName', 'snapshotName', 'cloneFolder', 'resourcePool', 'cloneBehaviour', 'maxInstances'],
        selectors: {
            imagesSelect: '#image',
            activeCloneBehaviour: ".cloneBehaviourRadio:checked"
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
            this.$addImageButtons = $j('#vmwareAddImageButton');
            this.$fetchOptionsError = $j("#error_fetch_options");

            this._lastImageId = this._imagesDataLength = 0;
            this._initImagesData();
            this._initTemplates();
            this._bindHandlers();
            this.refreshOptions();
            this.renderImagesTable();
        },
        refreshOptions: function () {
            var self = this,
                $loader = $j(BS.loadingIcon).clone();

            if ( ! this.validateServerSettings()) {
                return false;
            }

            this.$fetchOptionsButton.attr('disabled', true);
            $loader.insertAfter(this.$fetchOptionsButton);

            BS.ajaxRequest(this.refreshOptionsUrl, {
                parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
                onComplete: function () {
                    $loader.remove();
                    self.$fetchOptionsButton.attr('disabled', false);
                },
                onFailure: function (response) {
                    self.addError("Unable to fetch options: " + response.getStatusText());
                },
                onSuccess: function (response) {
                    var $response = $j(response.responseXML),
                            $errors = $response.find("errors:eq(0) error"),
                            $vms = $response.find('VirtualMachines:eq(0) VirtualMachine'),
                            $pools = $response.find('ResourcePools:eq(0) ResourcePool'),
                            $folders = $response.find('Folders:eq(0) Folder');

                    if ($errors.length) {
                        self.addError("Unable to fetch options: " + $errors.text());
                    } else if ($vms.length) {
                        self.showOptions($vms, $pools, $folders);
                    }
                }
            });

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        renderImagesTable: function () {
            if (this._imagesDataLength) {
                Object.keys(this.imagesData).forEach(function (imageId) {
                    this._renderImageRow(this.imagesData[imageId], imageId);
                }.bind(this));
            }
            this._toggleImagesTable(!!this._imagesDataLength);
        },
        showOptions: function ($vms, $pools, $folders) {
            this
                    ._displayImagesSelect($vms)
                    ._displayPoolsSelect($pools)
                    ._displayFoldersSelect($folders);

            this.$options.show();
        },
        validateServerSettings: function () {
            var url = $j("#${cons.serverUrl}").val(),
                isValid = (/^https:\/\/.*\/sdk$/).test(url);

            this.clearErrors();

            if (!isValid) {
                this.addError("Server URL doesn't seem to be correct. <br/>" +
                "Correct URL should look like this: <strong>https://vcenter/sdk</strong>");
            }

            return isValid;
        },
        validateOptions: function () {
            var maxInstances = this.$maxInstances.val(),
                isValid = true;

            // checking properties
            this.clearErrors($j("#error_image").add("#error_max_instances"));

            if ( ! this.$image.val()) {
                this.addError("Please select a VM", $j("#error_image"));
                isValid = false;
            }

            if (maxInstances != '' && ! $j.isNumeric(maxInstances)) {
                this.addError("Must be number", $j("#error_max_instances"));
                isValid = false;
            }

            return isValid;
        },
        addImage: function () {
            var newImage,
                newImageId;

            if (this.validateOptions()) {
                newImageId = this._lastImageId++;
                newImage = {
                    vmName: this.$image.val(),
                    snapshotName: this._visibleValue(this.$snapshot) || '[Latest Version]',
                    cloneFolder: this._visibleValue(this.$cloneFolder),
                    resourcePool: this._visibleValue(this.$resourcePool),
                    cloneBehaviour: $j(this.selectors.activeCloneBehaviour).val(),
                    maxInstances: this._visibleValue(this.$maxInstances) || '0'
                };
                this._renderImageRow(newImage, newImageId);
                this.imagesData[newImageId] = newImage;
                this._imagesDataLength += 1;
                this.saveImagesData();
                this._toggleImagesTable(!!this._imagesDataLength);
            }

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        removeImage: function ($elem) {
            delete this.imagesData[$elem.data('imageId')];
            this._imagesDataLength -= 1;
            $elem.parents('.imagesTableRow').remove();
            this.saveImagesData();
            this._toggleImagesTable(!!this._imagesDataLength);
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
        fetchSnapshots: function () {
            BS.ajaxRequest(this.refreshSnapshotsUrl, {
                parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
                onFailure: function (response) {
                    console.error('something went wrong', response);
                },
                onSuccess: function (response) {
                    var $response = $j(response.responseXML);

                    if ($response.length) {
                        this._displaySnapshotSelect($response.find('Snapshots:eq(0) Snapshot'));
                    }
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

            this.$fetchOptionsButton.on('click', this._fetchOptionsClickHandler.bind(this));
            this.$addImageButtons.on('click', this.addImage.bind(this));
            this.$options.on('change', this.selectors.imagesSelect, this.fetchSnapshots.bind(this));
            $j("[name=cloneBehaviour]").on('change', function () {
                var isClone = $j(this.selectors.activeCloneBehaviour).val() !== 'START',
                    $elementsToToggle = $j('.cloneOptionsRow');

                $elementsToToggle.toggle(isClone);
            }.bind(this));
            this.$image.add(this.$snapshot).on('change', this.validateOptions.bind(this));
            this.$imagesTable.on('click', '.removeVmImageLink', function () {
                if (confirm('Are you sure?')) {
                    self.removeImage($j(this));
                }
                return false;
            });
        },
        _fetchOptionsClickHandler: function () {
            if (this.$fetchOptionsButton.attr('disabled') !== 'true') { // it may be undefined
                this.refreshOptions();
            }

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        _renderImageRow: function (rows, id) {
            var $row = $j(this.templates.imagesTableRow);

            this._dataKeys.forEach(function (className) {
                $row.find('.' + className).text(rows[className]);
            });
            $row.find('.removeVmImageLink').data('imageId', id);
            this.$imagesTable.append($row);
        },
        _toggleImagesTable: function (show) {
            this.$imagesTableWrapper.show();
            this.$emptyImagesListMessage.toggle(!show);
            this.$imagesTable.toggle(show);
        },
        _displayImagesSelect: function ($vms) {
            var self = this,
                $select = $j(this.templates.imagesSelect),
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
            $pools.each(function () {
                self._appendOption(self.$resourcePool, $j(this).attr('name'));
            });

            return this;
        },
        _displayFoldersSelect: function ($folders) {
            var self = this;

            this.$cloneFolder.children().remove();
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
        _visibleValue: function (elem) {
            return elem.is(":visible") ? elem.val() : '';
        },
        _appendOption: function ($target, value) {
            $target.append($j('<option>').attr('value', value).text(value));
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
<td><a href="#" class="removeVmImageLink">X</a></td>\
            </tr>'),
                imagesSelect: $j('<select name="prop:image" id="image">\
    <option value="">--Please select a VM--</option>\
    <optgroup label="Virtual machines" class="vmGroup"></optgroup>\
    <optgroup label="Templates" class="templatesGroup"></optgroup>\
</select>')
            }
        }
    };
</script>

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
        <th>Delete</th>
      </tr>
      </tbody>
    </table>
    <props:hiddenProperty name="${cons.imagesData}"/>
  </td>
</tr>

<tr class="vmWareSphereOptions hidden">
    <td colspan="2">
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
              <props:selectProperty name="image"/>
            </div>
            <span id="error_image" class="error"></span>
          </td>
        </tr>

        <tr class="hidden cloneOptionsRow"  id="tr_snapshot_name">
          <th><label for="snapshot">Snapshot name:</label></th>
          <td>
            <select id="snapshot"> </select>
          </td>
        </tr>


        <tr class="hidden cloneOptionsRow">
          <th>
            <label for="cloneFolder">Folder for clones</label>
          </th>
          <td>
            <select id="cloneFolder"> </select>
          </td>
        </tr>

        <tr class="hidden cloneOptionsRow">
          <th>
            <label for="resourcePool">Resource pool</label>
          </th>
          <td>
            <select id="resourcePool"> </select>
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
            <span id="error_max_instances" class="error"></span>
          </td>
        </tr>
        <tr>
          <td colspan="2">
            <forms:button title="Add image" id="vmwareAddImageButton">Add image</forms:button>
          </td>
        </tr>
        </table>
    </td>
</tr>
<script type="text/javascript">
    BS.Clouds.VMWareVSphere.init();
</script>
