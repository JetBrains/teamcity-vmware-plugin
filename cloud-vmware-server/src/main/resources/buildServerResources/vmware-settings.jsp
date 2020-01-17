<%--
  ~ Copyright 2000-2020 JetBrains s.r.o.
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
<%@ taglib prefix="intprop" uri="/WEB-INF/functions/intprop" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<%--@elvariable id="resPath" type="java.lang.String"--%>
<jsp:useBean id="webCons" class="jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants"/>
<jsp:useBean id="cloudWebCons" class="jetbrains.buildServer.clouds.server.web.CloudWebConstants"/>
<jsp:useBean id="agentPools" scope="request" type="java.util.Collection<jetbrains.buildServer.serverSide.agentPools.AgentPool>"/>

<jsp:useBean id="refreshablePath" class="java.lang.String" scope="request"/>
<jsp:useBean id="refreshSnapshotsPath" class="java.lang.String" scope="request"/>
<jsp:useBean id="configurationHelperPath" class="java.lang.String" scope="request"/>
</table>

<script type="text/javascript">
    BS.LoadStyleSheetDynamically("<c:url value='${resPath}vmware-settings.css'/>");
</script>

<table class="runnerFormTable">
  <tr>
    <th><label for="${webCons.serverUrl}">vCenter SDK URL:<l:star/></label><bs:help
        urlPrefix="https://pubs.vmware.com/vsphere-51/topic/com.vmware.vsphere.install.doc/" file="GUID-191D86C8-EEF4-4198-9C11-2E0F25D2AB89.html"/></th>
    <td>
      <props:hiddenProperty name="${webCons.forceTrustManager}" />
      <props:textProperty name="${webCons.serverUrl}" className="settings longField"/>
      <span id="error_${webCons.serverUrl}" class="error"></span>
    </td>
  </tr>

  <tr>
    <th><label for="${webCons.username}">Username:<l:star/></label></th>
    <td>
      <props:textProperty name="${webCons.username}" className="settings longField"/>
      <span id="error_${webCons.username}" class="error"></span>
    </td>
  </tr>

  <tr>
    <th><label for="secure:${webCons.password}">Password:<l:star/></label></th>
    <td>
      <props:passwordProperty name="secure:${webCons.password}" className="settings longField"/>
      <span id="error_secure:${webCons.password}" class="error"></span>
    </td>
  </tr>
  <tr>
    <th><label for="${webCons.profileInstanceLimit}">Maximum instances count:</label></th>
    <td>
      <props:textProperty name="${webCons.profileInstanceLimit}" className="settings"/>
      <span id="error_${webCons.profileInstanceLimit}" class="error"></span>
      <span class="smallNote">Maximum number of instances that can be started. Use blank to have no limit</span>
    </td>
  </tr>
</table>

<div class="buttonsWrapper">
  <span id="error_fetch_options" class="error"></span>
  <div class="hidden options-loader"><i class="icon-refresh icon-spin ring-loader-inline"></i>&nbsp;Fetching parameter values from VMware vSphere...</div>
  <div>
    <forms:button id="vmwareFetchOptionsButton">Check connection / Fetch parameter values</forms:button>
  </div>
</div>

<div class="buttonsWrapper">
  <div class="imagesTableWrapper hidden">
    <table id="vmwareImagesTable" class="settings imagesTable hidden">
      <tbody>
      <tr>
        <th class="name">Agent image</th>
        <th class="name">Snapshot</th>
        <th class="name hidden">Clone folder</th>
        <th class="name hidden">Resource pool</th>
        <th class="name">Behavior</th>
        <th class="name maxInstances">Max # of instances</th>
        <th class="name" colspan="2"></th>
      </tr>
      </tbody>
    </table>
    <jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
    <c:set var="sourceImagesJson" value="${propertiesBean.properties['source_images_json']}"/>
    <input type="hidden" class="jsonParam" name="prop:source_images_json" id="source_images_json" value="<c:out value='${sourceImagesJson}'/>" data-err-id="source_images_json"/>
    <span class="error" id="error_source_images_json"></span>
  </div>
  <forms:addButton title="Add image" id="vmwareShowDialogButton">Add image</forms:addButton>
</div>

<input type="hidden" name="prop:image" id="realImageInput"/><!-- this one is required for getting snapshots -->
<input type="hidden" name="prop:helperFieldValue" id="helperFieldValue">
<input type="hidden" name="prop:helperFieldId" id="helperFieldId">
<bs:dialog dialogId="VMWareImageDialog" title="Add Image" closeCommand="BS.VMWareImageDialog.close()"
           dialogClass="VMWareImageDialog vmWareSphereOptions" titleId="VMWareImageDialogTitle"
        ><div class="message-wrapper"><div class="fetchingServerOptions message message_hidden"><i class="icon-refresh icon-spin ring-loader-inline"></i>Fetching server options...</div>
        <div class="fetchingSnapshots message message_hidden"><i class="icon-refresh icon-spin ring-loader-inline"></i>Fetching snapshots...</div>
    </div>
    <table class="runnerFormTable paramsTable">

        <tr>
            <th>Agent image:<l:star/></th>
            <td>
                <div>
                    <select name="_image" id="image" class="longField" data-id="sourceVmName" data-err-id="sourceVmName"></select>
                </div>
                <span class="error option-error option-error_sourceVmName"></span>
            </td>
        </tr>
            <tr class="hidden cloneOptionsRow advancedSetting">
              <th class="noBorder"><label for="nickname">Custom image name:</label></th>
              <td class="noBorder">
                <div>
                  <input type="text" id="nickname" value="" class="longField" data-id="nickname" data-err-id="nickname"/>
                  <div class="smallNoteAttention">Allows using the same VM as a source in multiple cloud images. <br/> Cloned agents' names will be based on this naming pattern.</div>
                  <span class="error option-error option-error_nickname"></span>
                </div>
              </td>
            </tr>
            <tr>
                <th>Behavior:<l:star/><bs:help file="Setting+Up+TeamCity+for+VMWare+vSphere+and+vCenter#SettingUpTeamCityforVMWarevSphereandvCenter-Features"/></th>
                <td>
                    <input type="hidden" class="behaviour__value" data-id="behaviour" data-err-id="behaviour"/>
                    <div>
                        <input type="radio" checked id="cloneBehaviour_FRESH_CLONE" name="cloneBehaviour" value="FRESH_CLONE" class="behaviour__switch behaviour__switch_radio"/>
                        <label for="cloneBehaviour_FRESH_CLONE">Clone the selected Virtual Machine or Template before starting</label>
                        <div class="grayNote">The clones will be deleted after stopping</div>
                    </div>
                  <div>
                      <input type="radio" id="cloneBehaviour_ON_DEMAND_CLONE" name="cloneBehaviour" value="ON_DEMAND_CLONE" class="behaviour__switch"/>
                      <label for="cloneBehaviour_ON_DEMAND_CLONE">Clone the selected Virtual Machine or Template, preserve the clone after stopping</label>
                      <div class="grayNote">The clones will be reused</div>
                  </div>
                  <div>
                    <input type="radio" id="cloneBehaviour_START_STOP" name="cloneBehaviour" value="START_STOP" class="behaviour__switch behaviour__switch_radio"/>
                    <label for="cloneBehaviour_START_STOP">Start the selected Virtual Machine</label>
                    <div class="grayNote">When idle, the Virtual Machine will be stopped as per profile settings</div>
                  </div>
                  <span class="error option-error option-error_behaviour"></span>
              </td>
            </tr>
            <tr class="hidden cloneOptionsRow"  id="tr_snapshot_name">
                <th>Snapshot name:<l:star/></th>
                <td>
                    <select id="snapshot" class="longField" data-id="snapshot" data-err-id="snapshot"></select>
                    <div class="smallNoteAttention currentStateWarning hidden">&laquo;Current state&raquo; requires a full clone, it is a time- and disk-space-consuming operation</div>
                    <span class="error option-error option-error_snapshot"></span>
                </td>
            </tr>


            <tr class="hidden cloneOptionsRow">
                <th>Folder for clones:<l:star/></th>
                <td>
                    <select id="cloneFolder" class="longField" data-id="folder" data-err-id="folder"></select>
                    <span class="error option-error option-error_folder"></span>
                </td>
            </tr>

            <tr class="hidden cloneOptionsRow">
                <th>Resource pool:<l:star/></th>
                <td>
                    <select id="resourcePool" class="longField" data-id="pool" data-err-id="pool"></select>
                    <span class="error option-error option-error_pool"></span>
                </td>
            </tr>
            <tr class="hidden cloneOptionsRow advancedSetting">
              <th>Customization spec:</th>
              <td>
                <select id="customizationSpec" class="longField" data-id="customizationSpec" data-err-id="customizationSpec"></select>
                <span class="error option-error option-error_customizationSpec"></span>
              </td>
            </tr>
            <tr class="hidden cloneOptionsRow">
                <th>Max number of instances:<l:star/></th>
                <td>
                    <div>
                        <input type="text" id="maxInstances" value="" class="longField" data-id="maxInstances" data-err-id="maxInstances"/>
                    </div>
                    <span class="error option-error option-error_maxInstances"></span>
                </td>
            </tr>
            <tr class="advancedSetting">
              <th><label for="${cloudWebCons.agentPoolIdField}">Agent pool:</label></th>
              <td>
                <select id="${cloudWebCons.agentPoolIdField}" data-id="${cloudWebCons.agentPoolIdField}" class="longField configParam">
                  <props:option value=""><c:out value="<Please select agent pool>"/></props:option>
                  <c:forEach var="ap" items="${agentPools}">
                    <props:option value="${ap.agentPoolId}"><c:out value="${ap.name}"/></props:option>
                  </c:forEach>
                </select>
                <span id="error_${cloudWebCons.agentPoolIdField}" class="error"></span>
              </td>
            </tr>

        </table>

  <admin:showHideAdvancedOpts containerId="VMWareImageDialog" optsKey="vmwareCloudSettings"/>
  <admin:highlightChangedFields containerId="VMWareImageDialog"/>

  <div class="popupSaveButtonsBlock">
        <forms:submit label="Add" type="button" id="vmwareAddImageButton"/>
        <forms:button title="Cancel" id="vmwareCancelAddImageButton">Cancel</forms:button>
    </div>
</bs:dialog>

<script type="text/javascript">
    $j.ajax({
        url: "<c:url value="${resPath}vmware-settings.js"/>",
        dataType: "script",
        success: function () {
            BS.Clouds.VMWareVSphere.init('<c:url value="${refreshablePath}"/>',
                    '<c:url value="${refreshSnapshotsPath}"/>',
                    '<c:url value="${configurationHelperPath}"/>',
                    'source_images_json',
                    '${webCons.serverUrl}');
        },
        cache: true
    });
</script>
<table class="runnerFormTable" style="margin-top: 3em;">