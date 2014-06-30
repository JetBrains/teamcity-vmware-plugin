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
        _imagesDataSelector: '#${cons.imagesData}',
        init: function () {
            var self = this;

            this.$fetchOptionsButton = $j('#vmwareFetchOptionsButton');
            this.$addImageButtons = $j('#vmwareAddImageButton');
            this.$image = $j('#image');
            this.$snapshot = $j('#snapshot');
            this.$imagesList = $j("#vmware_images_list");
            this.$imagesDataElem = $j(this._imagesDataSelector);

            this.$fetchOptionsButton.on('click', this.refreshOptions.bind(this));
            this.$addImageButtons.on('click', this.addImage.bind(this));
            this.$image.on('change', this.readSnapshots.bind(this));
            $j('#cloneBehaviour, #snapshot, #image').on('change', this.validateOptions.bind(this));
            $j(".images-list-wrapper").on('click', '.removeVmImageLink', function () {
                if (confirm('Are you sure?')) {
                    self.removeImage($j(this));
                }
                return false;
            });

            this.refreshOptions();
            this.showImagesList();
        },
        _appendOption: function ($target, value) {
            $target.append($j('<option>').attr('value', value).text(value));
        },
        getImagesData: function () {
            var rawImagesData = this.$imagesDataElem.val(),
                    imagesData = rawImagesData && rawImagesData.split(';X;:') || [];

            return imagesData.map(function (imageDataStr) {
                var props = imageDataStr.split(';');

                // drop images without vmName
                if (!props[0].length) {
                    props = [];
                }

                return props;
            }).filter(function (imageData) {
                return !!imageData.length;
            });
        },
        setImagesData: function (data) {
            this.$imagesDataElem.val(data);
        },
        updateImagesData: function () {
            var data = "";

            this.setImagesData('');
            this.$imagesList.find('tr').each(function () {
                if ($j(this).children("td").size()) {
                    $j(this).children("td").each(function () {
                        data += $j(this).text().replace("[Latest version]", "")+ ";";
                    });

                    data += ":";
                }
            });
            this.setImagesData(data);
        },
        refreshOptions: function () {
            var self = this,
                $loader = $j(BS.loadingIcon);

            if (this.$fetchOptionsButton.attr('disabled')) {
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
                    console.error('something went wrong', response);
                },

                onSuccess: function (response) {
                    var $response = $j(response.responseXML),
                            $vms = $response.find('VirtualMachines:eq(0) VirtualMachine'),
                            $pools = $response.find('ResourcePools:eq(0) ResourcePool'),
                            $folders = $response.find('Folders:eq(0) Folder');

                    if (!$vms.length) {
                        return;
                    }

                    self.$image
                            .find('option, optgroup').remove().end()
                            .append($j('<option>').val('').text('--Please select a VM--'))
                            .append($j('<optgroup>').attr('label', 'Virtual machines'));

                    $vms.each(function () {
                        if ($j(this).attr('template') == 'false') {
                            self._appendOption(self.$image.find("optgroup[label='Virtual machines']"), $j(this).attr('name'));
                        }
                    });

                    self.$image.append($j("<optgroup>").attr("label", "Templates"));

                    $vms.each(function () {
                        if ($j(this).attr('template') == 'true') {
                            self._appendOption(self.$image.find("optgroup[label='Templates']"), $j(this).attr('name'));
                        }
                    });

                    $j("#resourcePool").find("option").remove();
                    $pools.each(function () {
                        self._appendOption($j('#resourcePool'), $j(this).attr('name'));
                    });

                    $j("#cloneFolder").find("option").remove();
                    $folders.each(function () {
                        self._appendOption($j("#cloneFolder"), $j(this).attr('name'));
                    });

                    self.showOptions();
                }
            });

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        validateOptions: function () {
            var cloneFolder = $j("#cloneFolder").val(),
                    resourcePool = $j("#resourcePool").val(),
                    cloneBehaviour = $j("#cloneBehaviour").val(),
                    maxInstances = $j("#maxInstances").val(),
                    isValid = true;

            // checking properties
            $j("#error_image").empty();
            $j("#error_cloneBehaviour").empty();

            if (!this.$image.val()) {
                $j("#error_image").append($j("<div>").text("Please select a VM"));
                isValid = false;
            }
            if (this.$image.find(":selected").parent().attr("label") == 'Templates' && cloneBehaviour == 'START') {
                $j("#error_cloneBehaviour").append($j("<div>").text("Start/Stop mode is not available for readonly(template) VMs"));
                isValid = false;
            }
            if ( !this.$snapshot.val()  && cloneBehaviour == "LINKED_CLONE") {
                $j("#error_cloneBehaviour").append($j("<div>").text("Linked clone mode requires an existing snapshot"));
                isValid = false;
            }

            return isValid;
        },
        addImage: function () {
            var vmName = $j("#image option:selected").text(),
                    snapshotName = $j("#snapshot option:selected").text(),
                    cloneFolder = $j("#cloneFolder").val(),
                    resourcePool = $j("#resourcePool").val(),
                    cloneBehaviour = $j("#cloneBehaviour").val(),
                    maxInstances = $j("#maxInstances").val();

            if (this.validateOptions()) {
                this._addImage(vmName, snapshotName, cloneFolder, resourcePool, cloneBehaviour, maxInstances);
                this.updateImagesData();
            }

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        _addImage: function (vmName, snapshotName, cloneFolder, resourcePool, cloneBehaviour, maxInstances) {
            this.$imagesList.append($j("<tr>")
                .append($j("<td>").text(vmName))
                .append($j("<td>").text(snapshotName))
                .append($j("<td>").text(cloneFolder))
                .append($j("<td>").text(resourcePool))
                .append($j("<td>").text(cloneBehaviour))
                .append($j("<td>").text(maxInstances))
                .append($j("<td>")
                    .append($j("<a>")
                        .attr('href', '#')
                        .addClass('removeVmImageLink')
                        .text("X")))
            );
        },
        removeImage: function ($elem) {
            $j(this).closest('tr').remove();
            self.updateImagesData();
        },
        showImagesList: function () {
            var self = this,
                imagesData = this.getImagesData();

            if (imagesData) {
                imagesData.each(function (imageData) {
                    self._addImage.apply(self, imageData);
                });
                $j(".images-list-wrapper").show(200);
            }
        },
        readSnapshots: function () {
            var self = this;

            BS.ajaxRequest(this.refreshSnapshotsUrl, {
                parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
                onFailure: function (response) {
                    console.error('something went wrong', response);
                },
                onSuccess: function (response) {
                    var $response = $j(response.responseXML),
                        $snapshots;

                    if ( ! $response.length) return;

                    $snapshots = $response.find('Snapshots:eq(0) Snapshot');

                    self.$snapshot
                        .find("option").remove().end()
                        .append("<option value=''>[Latest version]</option>");

                    $snapshots.each(function () {
                        self._appendOption(self.$snapshot, $j(this).attr("name"));
                    });

                }.bind(this)
            });
        },
        showOptions: function () {
            $j('#newProfilesContainerInner').find('.vmWareSphereOptions').show();
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
      <%--<input type="hidden" id="refreshablePath" value="<c:url value="${refreshablePath}"/>"/>--%>
      <%--<input type="button" value="Fetch options" id="vmwareFetchOptionsButton"/>--%>
      <forms:button id="vmwareFetchOptionsButton">Fetch options</forms:button>
    </td>
  </tr>

<tr class="images-list-wrapper hidden">
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
        <tr>
          <th><label for="image">Agent images:</label></th>
          <td>
            <div>
              <props:selectProperty name="image"/>
            </div>
            <span id="error_image" class="error"></span>
          </td>
        </tr>

        <tr>
          <th><label for="snapshot">Snapshot name:</label></th>
          <td>
            <props:selectProperty name="snapshot"/>
          </td>
        </tr>


        <tr>
          <th>
            <label for="cloneFolder">Folder for clones</label>
          </th>
          <td>
            <props:selectProperty name="cloneFolder"/>
          </td>
        </tr>

        <tr>
          <th>
            <label for="cloneBehaviour">Start behaviour</label>
          </th>
          <td>
            <props:selectProperty name="cloneBehaviour">
              <props:option value="START">Start/Stop</props:option>
              <props:option value="CLONE">Clone</props:option>
              <props:option value="LINKED_CLONE">Linked clone</props:option>
              <props:option value="ON_DEMAND_CLONE">On demand clone</props:option>
            </props:selectProperty>
            <span id="error_cloneBehaviour" class="error"></span>
            <%--<span class="smallNote">Linked clone mode requires an existing snapshot</span>--%>
          </td>
        </tr>
          <tr>
            <th>
              <label for="cloneType">Clone type</label>
            </th>
            <td>
              <props:selectProperty name="cloneType">
                <props:option value="DEFINED">Defined</props:option>
                <props:option value="CURRENT_STATE">Current state</props:option>
                <props:option value="LATEST_SNAPSHOT">Latest snapshot</props:option>
              </props:selectProperty>
              <span id="error_cloneType" class="error"></span>
              <%--<span class="smallNote">Linked clone mode requires an existing snapshot</span>--%>
            </td>
          </tr>

        <tr>
          <th>
            <label for="maxInstances">Max number of instances</label>
          </th>
          <td>
            <props:textProperty name="maxInstances"/>
          </td>
        </tr>
        <tr>
          <th>
            <label for="resourcePool">Resource pool</label>
          </th>
          <td>
            <props:selectProperty name="resourcePool"/>
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
