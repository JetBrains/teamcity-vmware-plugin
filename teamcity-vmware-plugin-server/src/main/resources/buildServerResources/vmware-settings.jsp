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
            $j("[id^=cloneBehaviour]").each(function(){
              $j(this).on('change', self.showNecessaryOptions.bind(self));
            });
            $j('#snapshot, #image').on('change', this.validateOptions.bind(this));
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
        showNecessaryOptions: function() {
          var clone = $j("input:radio[name='prop:cloneBehaviour']:checked").val() == 'START';
          if (clone){
            $j("#tr_resource_pool").hide("fast");
            $j("#tr_clone_folder").hide("fast");
            $j("#tr_snapshot_name").hide("fast");
            $j("#tr_max_instances").hide("fast");
          } else {
            $j("#tr_resource_pool").show("fast");
            $j("#tr_clone_folder").show("fast");
            $j("#tr_snapshot_name").show("fast");
            $j("#tr_max_instances").show("fast");
          }
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
          $j("#error_fetch_options").empty();

          var url = $j("#${cons.serverUrl}").val();
          if (url.lastIndexOf("https://", 0) != 0
              || url.lastIndexOf("/sdk") != url.length-4) {
            $j("#error_fetch_options").append($j("<div>").html("Server URL doesn't seem to be correct. <br/>" +
                                                               "Correct URL should look like this: <strong>https://vcenter/sdk</strong>"));
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
                  $j("#error_fetch_options").append($j("<div>").text("Unable to fetch options: " + response.getStatusText()));
                },

                onSuccess: function (response) {

                    var $response = $j(response.responseXML),
                            $errors = $response.find("errors:eq(0) error"),
                            $vms = $response.find('VirtualMachines:eq(0) VirtualMachine'),
                            $pools = $response.find('ResourcePools:eq(0) ResourcePool'),
                            $folders = $response.find('Folders:eq(0) Folder');

                  if ($errors.length){
                    $j("#error_fetch_options").append($j("<div>").text("Unable to fetch options: " + $errors.text()));
                    return;
                  }

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
            $j("#error_max_instances").empty();

            if (!this.$image.val()) {
                $j("#error_image").append($j("<div>").text("Please select a VM"));
                isValid = false;
            }

          if (maxInstances != '' && !$j.isNumeric(maxInstances)){
            $j("#error_max_instances").append($j("<div>").text("Must be number"));
            isValid = false;
          }

            return isValid;
        },
        _visibleValue: function(elem) {
          if (elem.is(":visible")){
            return elem.val();
          } else {
            return "";
          }
        },
        addImage: function () {
            var vmName = $j("#image option:selected").text();
            var snapshotName = this._visibleValue($j("#snapshot")),
                    cloneFolder = this._visibleValue($j("#cloneFolder")),
                    resourcePool = this._visibleValue($j("#resourcePool")),
                    cloneBehaviour = this._visibleValue($j("#cloneBehaviour")),
                    maxInstances = this._visibleValue($j("#maxInstances"));

            if (this.validateOptions()) {
                this._addImage(vmName, snapshotName, cloneFolder, resourcePool, cloneBehaviour, maxInstances);
                this.updateImagesData();
            }

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        _addImage: function (vmName, snapshotName, cloneFolder, resourcePool, cloneBehaviour, maxInstances) {
          if (snapshotName==''){
            snapshotName='[Latest version]';
          }
          if (maxInstances ==''){
            maxInstances = '0';
          }
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
            $elem.closest('tr').remove();
            this.updateImagesData();
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
      <span id="error_fetch_options" class="error"></span>
      <div>
        <forms:button id="vmwareFetchOptionsButton">Fetch options</forms:button>
      </div>
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
        <tr id="tr_clone_behaviour">
          <th>Select an image type:</th>
          <td>
            <props:radioButtonProperty id="cloneBehaviour_START" name="cloneBehaviour" value="START" checked="true"/>
            <label for="cloneBehaviour">Start/Stop instance</label>
            <br/>
            <props:radioButtonProperty id="cloneBehaviour_CLONE" name="cloneBehaviour" value="CLONE"/>
            <label for="cloneBehaviour">Fresh clone</label>
            <br/>
            <props:radioButtonProperty id="cloneBehaviour_ON_DEMAND_CLONE" name="cloneBehaviour" value="ON_DEMAND_CLONE"/>
            <label for="cloneBehaviour">On demand clone</label>
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
            <props:selectProperty name="snapshot"/>
          </td>
        </tr>


        <tr class="hidden" id="tr_clone_folder">
          <th>
            <label for="cloneFolder">Folder for clones</label>
          </th>
          <td>
            <props:selectProperty name="cloneFolder"/>
          </td>
        </tr>

        <tr class="hidden" id="tr_resource_pool">
          <th>
            <label for="resourcePool">Resource pool</label>
          </th>
          <td>
            <props:selectProperty name="resourcePool"/>
          </td>
        </tr>
        <tr class="hidden" id="tr_max_instances">
          <th>
            <label for="maxInstances">Max number of instances</label>
          </th>
          <td>
            <div>
              <props:textProperty name="maxInstances" value="0"/>
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
