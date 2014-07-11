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
        _lastImageId: 0,
        init: function () {
            this.imagesData = { length: 0 };
            this.$fetchOptionsButton = $j('#vmwareFetchOptionsButton');
            this.$addImageButtons = $j('#vmwareAddImageButton');
            this.$options = $j('vmWareSphereOptions');
            this.$image = $j('#image');
            this.$snapshot = $j('#snapshot');
            this.$imagesTableWrapper = $j('.images-table-wrapper');
            this.$imagesTable = $j("#vmware_images_list");
            this.$imagesDataElem = $j('#${cons.imagesData}');
            this.$fetchOptionsError = $j("#error_fetch_options");
            this.$cloneFolder = $j("#cloneFolder");
            this.$resourcePool = $j("#resourcePool");
            this.$maxInstances = $j("#maxInstances");
            this.$cloneBehaviour = $j("input[name='cloneBehaviour']:checked");

            this._initImagesData();
            this._bindHandlers();
            this.refreshOptions();
            this.renderImagesTable();
        },
        saveImagesData: function () {
            var data = '';

            Object.keys(this.imagesData).forEach(function (imageId) {
                data += this.imagesData[imageId].join(';X;:').replace('[Latest version]', '') + ';X;:';
            }.bind(this));

            this.$imagesDataElem.val(data);
        },
        toggleCloneOptions: function () {
            var isClone = this.$cloneBehaviour.val() !== 'START',
                $elementsToToggle = $j('#tr_resource_pool, #tr_clone_folder, #tr_snapshot_name, #tr_max_instances');

            $elementsToToggle.toggle(isClone);
        },
        clearErrors: function () {
            this.$fetchOptionsError.empty();
        },
        addError: function (errorHTML) {
            this.$fetchOptionsError
                .append($j("<div>").html(errorHTML));
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
        refreshOptions: function () {
            var self = this,
                $loader = $j(BS.loadingIcon);

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
        validateOptions: function () {
            var cloneBehaviour = .val(),
                maxInstances = this.$maxInstances.val(),
                isValid = true;

            // checking properties
            $j("#error_image").add("#error_max_instances").empty();

            if ( ! this.$image.val()) {
                $j("#error_image").append($j("<div>").text("Please select a VM"));
                isValid = false;
            }

            if (maxInstances != '' && !$j.isNumeric(maxInstances)) {
                $j("#error_max_instances").append($j("<div>").text("Must be number"));
                isValid = false;
            }

            return isValid;
        },
        addImage: function () {
            var newImage,
                newImageId;

            if (this.validateOptions()) {
                newImageId = this._lastImageId++;
                newImage = [
                    this.$image.val(),
                    this._visibleValue(this.$snapshot),
                    this._visibleValue(this.$cloneFolder),
                    this._visibleValue(this.$resourcePool),
                    this.$cloneBehaviour.val(),
                    this._visibleValue(this.$maxInstances)
                ];
                this._renderImageRow.apply(this, newImage.concat(newImageId));
                this.imagesData[newImageId] = newImage;
                this.imagesData.length++;
                this.saveImagesData();
            }

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        removeImage: function ($elem) {
            delete this.imagesData[$elem.data('imageId')];
            this.imagesData.length--;
            $elem.closest('tr').remove();
            this.saveImagesData();
        },
        renderImagesTable: function () {
            if (this.imagesData.length) {
                Object.keys(this.imagesData).forEach(function (imageId) {
                    this._renderImageRow.apply(this, this.imagesData[imageId].concat(imageId));
                }.bind(this));

                this._toggleImagesTable(true);
            }
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
        showOptions: function ($vms, $pools, $folders) {
            this
                ._displayImagesSelect($vms)
                ._displayPoolsSelect($pools)
                ._displayFoldersSelect($folders);

            this.$options.show();
        },
        _bindHandlers: function () {
            var self = this;

            this.$fetchOptionsButton.on('click', this._fetchOptionsClickHandler.bind(this));
            this.$addImageButtons.on('click', this.addImage.bind(this));
            this.$image.on('change', this.fetchSnapshots.bind(this));
            $j("[name=cloneBehaviour]").on('change', this.toggleCloneOptions.bind(this));
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
                    accumulator[id] = props;
                }
                return accumulator;
            }, {});
        },
        _renderImageRow: function (vmName, snapshotName, cloneFolder, resourcePool, cloneBehaviour, maxInstances, id) {
            this.$imagesTable
                .append($j("<tr>")
                    .append($j("<td>").text(vmName))
                    .append($j("<td>").text(snapshotName || '[Latest version]'))
                    .append($j("<td>").text(cloneFolder))
                    .append($j("<td>").text(resourcePool))
                    .append($j("<td>").text(cloneBehaviour))
                    .append($j("<td>").text(maxInstances || '0'))
                    .append($j("<td>")
                        .append($j("<a>")
                            .data('imageId', id)
                            .attr('href', '#')
                            .addClass('removeVmImageLink')
                            .text("X"))));
        },
        _toggleImagesTable: function (show) {
            this.$imagesTableWrapper.toggle(show);
        },
        _displayImagesSelect: function ($vms) {
            var self = this;

            this.$image
                    .find('option, optgroup').remove().end()
                    .append($j('<option>').val('').text('--Please select a VM--'))
                    .append($j('<optgroup>').attr('label', 'Virtual machines'));

            $vms.each(function () {
                if ($j(this).attr('template') == 'false') {
                    self._appendOption(self.$image.find("optgroup[label='Virtual machines']"), $j(this).attr('name'));
                }
            });

            this.$image.append($j("<optgroup>").attr("label", "Templates"));

            $vms.each(function () {
                if ($j(this).attr('template') == 'true') {
                    self._appendOption(self.$image.find("optgroup[label='Templates']"), $j(this).attr('name'));
                }
            });

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
            if (elem.is(":visible")) {
                return elem.val();
            } else {
                return "";
            }
        },
        _appendOption: function ($target, value) {
            $target.append($j('<option>').attr('value', value).text(value));
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

<tr class="images-table-wrapper hidden">
  <td colspan="2">
    <table id="vmware_images_list" class="runnerFormTable">
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
        <tr id="tr_clone_behaviour">
          <th>Select an image type:</th>
          <td>
            <input type="radio" id="cloneBehaviour_START" name="cloneBehaviour" value="START" checked="true"/>
            <label for="cloneBehaviour_START">Start/Stop instance</label>
            <br/>
            <input type="radio" id="cloneBehaviour_CLONE" name="cloneBehaviour" value="CLONE"/>
            <label for="cloneBehaviour_CLONE">Fresh clone</label>
            <br/>
            <input type="radio" id="cloneBehaviour_ON_DEMAND_CLONE" name="cloneBehaviour" value="ON_DEMAND_CLONE"/>
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

        <tr class="hidden"  id="tr_snapshot_name">
          <th><label for="snapshot">Snapshot name:</label></th>
          <td>
            <select id="snapshot"> </select>
          </td>
        </tr>


        <tr class="hidden" id="tr_clone_folder">
          <th>
            <label for="cloneFolder">Folder for clones</label>
          </th>
          <td>
            <select id="cloneFolder"> </select>
          </td>
        </tr>

        <tr class="hidden" id="tr_resource_pool">
          <th>
            <label for="resourcePool">Resource pool</label>
          </th>
          <td>
            <select id="resourcePool"> </select>
          </td>
        </tr>
        <tr class="hidden" id="tr_max_instances">
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
    </td></tr>
<script type="text/javascript">
    BS.Clouds.VMWareVSphere.init();
</script>
