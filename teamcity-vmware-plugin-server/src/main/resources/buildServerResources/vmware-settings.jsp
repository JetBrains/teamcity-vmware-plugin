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
        refreshOptions: function () {
            var $loader = $j(BS.loadingIcon);

            if ($j('#vmwareFetchOptionsButton').attr('disabled')) {
                return false;
            }

            $j('#vmwareFetchOptionsButton').attr('disabled', true);
            $loader.insertAfter($j('#vmwareFetchOptionsButton'));

            BS.ajaxRequest(this.refreshOptionsUrl, {
                parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),

                onComplete: function() {
                    $loader.remove();
                    $j('#vmwareFetchOptionsButton').attr('disabled', false);
                },

                onFailure: function (response) {
                    console.error('something went wrong', response);
                },

                onSuccess: function (response) {
                    var $root = $j(BS.Util.documentRoot(response)),
                        $vms = $root.find('VirtualMachines:eq(0) VirtualMachine'),
                        $pools = $root.find('ResourcePools:eq(0) ResourcePool'),
                        $folders = $root.find('Folders:eq(0) Folder'),
                        appendOption = function(target, value) {
                            target.append($j("<option>").attr("value", value).text(value));
                        };

                    if (! $vms.length) {
                        return;
                    }

                    $j('#image')
                        .find("option, optgroup").remove().end()
                        .append($j("<option>").val("").text("--Please select a VM--"))
                        .append($j("<optgroup>").attr("label", "Virtual machines"));

                    $vms.each(function () {
                        if ($j(this).attr('template') == 'false') {
                            appendOption($j("#image optgroup[label='Virtual machines']"), $j(this).attr('name'));
                        }
                    });

                    $j('#image').append($j("<optgroup>").attr("label", "Templates"));

                    $vms.each(function () {
                        if ($j(this).attr('template') == 'true') {
                            appendOption($j("#image optgroup[label='Templates']"), $j(this).attr('name'));
                        }
                    });

                    $j("#resourcePool").find("option").remove();
                    $pools.each(function () {
                        appendOption($j('#resourcePool'), $j(this).attr('name'));
                    });

                    $j("#cloneFolder").find("option").remove();
                    $folders.each(function () {
                        appendOption($j("#cloneFolder"), $j(this).attr('name'));
                    });

                    BS.Clouds.VMWareVSphere.showOptions();
                }
            });

            return false;
        },
        addImage: function () {
            var vmName = $j("#image option:selected").text(),
                snapshotName = $j("#snapshot option:selected").text(),
                cloneFolder = $j("#cloneFolder").val(),
                resourcePool = $j("#resourcePool").val(),
                cloneBehaviour = $j("#cloneBehaviour").val(),
                maxInstances = $j("#maxInstances").val();

            if (this.validateOptions()) {
                this.addImageInternal(vmName, snapshotName, cloneFolder, resourcePool, cloneBehaviour, maxInstances);
                this.updateHidden();
            }

            return false;
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

            if ($j("#image").val() == "") {
                $j("#error_image").append($j("<div>").text("Please select a VM"));
                isValid = false;
            }
            if ($j("#image option:selected").parent().attr("label") == 'Templates' && cloneBehaviour == 'START') {
                $j("#error_cloneBehaviour").append($j("<div>").text("Start/Stop mode is not available for readonly(template) VMs"));
                isValid = false;
            }
            if ($j("#snapshot").val() == "" && cloneBehaviour == "LINKED_CLONE") {
                $j("#error_cloneBehaviour").append($j("<div>").text("Linked clone mode requires an existing snapshot"));
                isValid = false;
            }

            return isValid;
        },
        restoreFromHidden: function () {
            var self = this,
                partsOfStr = $j("#${cons.imagesData}").val();

            if (partsOfStr) {
                partsOfStr.split(';X;:').each(function (partOfStr) {
                    var ones = partOfStr.split(';');

                    if (ones[0].length > 0) {
                        self.addImageInternal.apply(self, ones);
                    }
                });
                $j(".images-list-wrapper").show(200);
            }
        },
        addImageInternal: function (vmName, snapshotName, cloneFolder, resourcePool, cloneBehaviour, maxInstances) {
            $j("#vmware_images_list tbody").append($j("<tr>")
                .append($j("<td>").text(vmName))
                .append($j("<td>").text(snapshotName))
                .append($j("<td>").text(cloneFolder))
                .append($j("<td>").text(resourcePool))
                .append($j("<td>").text(cloneBehaviour))
                .append($j("<td>").text(maxInstances))
                .append($j("<td>")
                    .append($j("<a>").attr("href", "#").attr("onclick", "$j(this).closest('tr').remove();BS.Clouds.VMWareVSphere.updateHidden();return false;").text("X")))
            );
        },
        updateHidden: function () {
            var data = "";
            $j("#${cons.imagesData}").val("");
            $j("#vmware_images_list tbody tr").each(function () {
                if ($j(this).children("td").size()) {
                    $j(this).children("td").each(function () {
                        data += $j(this).text().replace("[Latest version]", "")+ ";";
                    });

                    data += ":";
                }
            });
            $j("#${cons.imagesData}").val(data);
        },
        readSnapshots: function () {
            BS.ajaxRequest(this.refreshSnapshotsUrl, {
                parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
                onSuccess: function (response) {
                    var root = BS.Util.documentRoot(response),
                        $snapshots;

                    if (!root) return;

                    $snapshots = $j(root).find('Snapshots:eq(0) Snapshot');

                    $j("#snapshot").find("option").remove();
                    $j("#snapshot").append("<option value=''>[Latest version]</option>");

                    $snapshots.each(function () {
                        $j("#snapshot").append($j('<option/>').val($j(this).attr("name")).text($j(this).attr("name")));
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
            </props:selectProperty>
            <span id="error_cloneBehaviour" class="error"></span>
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
    $j('#vmwareFetchOptionsButton').on('click', BS.Clouds.VMWareVSphere.refreshOptions.bind(BS.Clouds.VMWareVSphere));
    $j('#vmwareAddImageButton').on('click', BS.Clouds.VMWareVSphere.addImage.bind(BS.Clouds.VMWareVSphere));
    $j('#image').on('change', BS.Clouds.VMWareVSphere.readSnapshots.bind(BS.Clouds.VMWareVSphere));
    $j("#cloneBehaviour, #snapshot, #image").on('change', BS.Clouds.VMWareVSphere.validateOptions.bind(BS.Clouds.VMWareVSphere));

    BS.Clouds.VMWareVSphere.refreshOptions();
    BS.Clouds.VMWareVSphere.restoreFromHidden();
</script>
