<%--
  ~ /*
  ~  * Copyright 2000-2014 JetBrains s.r.o.
  ~  *
  ~  * Licensed under the Apache License, Version 2.0 (the "License");
  ~  * you may not use this file except in compliance with the License.
  ~  * You may obtain a copy of the License at
  ~  *
  ~  * http://www.apache.org/licenses/LICENSE-2.0
  ~  *
  ~  * Unless required by applicable law or agreed to in writing, software
  ~  * distributed under the License is distributed on an "AS IS" BASIS,
  ~  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~  * See the License for the specific language governing permissions and
  ~  * limitations under the License.
  ~  */
  --%>

<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include.jsp" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%--@elvariable id="resPath" type="java.lang.String"--%>
<jsp:useBean id="cons" class="jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants"/>

<jsp:useBean id="refreshablePath" class="java.lang.String" scope="request"/>
<jsp:useBean id="refreshSnapshotsPath" class="java.lang.String" scope="request"/>
</table>

<script type="text/javascript">
    BS.LoadStyleSheetDynamically("<c:url value='${resPath}vmware-settings.css'/>");
</script>

<table class="runnerFormTable">
  <tr>
    <th><label for="${cons.serverUrl}">Server URL: <l:star/></label></th>
    <td><props:textProperty name="${cons.serverUrl}" className="longField"/></td>
  </tr>

  <tr>
    <th><label for="${cons.username}">Username: <l:star/></label></th>
    <td><props:textProperty name="${cons.username}" className="longField"/></td>
  </tr>

  <tr>
    <th><label for="secure:${cons.password}">Password: <l:star/></label></th>
    <td><props:passwordProperty name="secure:${cons.password}" className="longField"/></td>
  </tr>
  <tr>
    <td colspan="2">
      <span id="error_fetch_options" class="error"></span>
      <div>
        <forms:button id="vmwareFetchOptionsButton">Fetch options</forms:button>
      </div>
    </td>
  </tr>
</table>

<div class="imagesOuterWrapper">
    <h3 class="title_underlined">Images</h3>
    <div class="imagesTableWrapper hidden">
        <span class="emptyImagesListMessage hidden">You haven't added any images yet.</span>
        <table id="vmwareImagesTable" class="settings imagesTable hidden">
          <tbody>
          <tr>
            <th class="name">Template or Machine</th>
            <th class="name">Snapshot</th>
            <th class="name hidden">Clone folder</th>
            <th class="name hidden">Resource pool</th>
            <th class="name">behaviour</th>
            <th class="name maxInstances">Max # of instances</th>
            <th class="name" colspan="2"></th>
          </tr>
          </tbody>
        </table>
        <props:hiddenProperty name="${cons.imagesData}"/>
    </div>
    <forms:addButton title="Add image" id="vmwareShowDialogButton">Add image</forms:addButton>
</div>

<i></i>
<input type="hidden" name="prop:image" id="realImageInput"/>
<bs:dialog dialogId="VMWareImageDialog" title="Add Image" closeCommand="BS.VMWareImageDialog.close()"
           dialogClass="VMWareImageDialog vmWareSphereOptions" titleId="VMWareImageDialogTitle"
        ><div class="message-wrapper"><div class="fetchingServerOptions message message_hidden"><i class="icon-refresh icon-spin"></i>Fetching server options...</div>
        <div class="fetchingSnapshots message message_hidden"><i class="icon-refresh icon-spin"></i>Fetching snapshots...</div>
    </div>
    <table class="runnerFormTable">

        <tr>
            <th>Source:&nbsp;<l:star/></th>
            <td>
                <div>
                    <select name="_image" id="image" class="longField" data-id="sourceName" data-err-id="sourceName"></select>
                </div>
                <span class="error option-error option-error_sourceName"></span>
            </td>
        </tr>

            <tr>
                <th>Behaviour:&nbsp;<l:star/></th>
                <td>
                    <input type="hidden" class="behaviour__value" data-id="behaviour" data-err-id="behaviour"/>
                    <div>
                        <input type="radio" id="cloneBehaviour_ON_DEMAND_CLONE" name="cloneBehaviour" value="ON_DEMAND_CLONE" class="behaviour__switch behaviour__switch_radio"/>
                        <label for="cloneBehaviour_ON_DEMAND_CLONE">On demand clone</label>
                    </div>
                    <div>
                        <input type="checkbox" id="cloneBehaviour_FRESH_CLONE" name="cloneBehaviour" value="FRESH_CLONE" class="behaviour__switch behaviour__switch_checkbox"/>
                        <label for="cloneBehaviour_FRESH_CLONE">Fresh clone</label>
                    </div>
                    <div>
                        <input type="radio" id="cloneBehaviour_START_STOP" name="cloneBehaviour" value="START_STOP" class="behaviour__switch behaviour__switch_radio"/>
                        <label for="cloneBehaviour_START_STOP">Start/Stop instance</label>
                    </div>
                    <span class="error option-error option-error_behaviour"></span>
                </td>
            </tr>

            <tr class="hidden cloneOptionsRow"  id="tr_snapshot_name">
                <th>Snapshot name:&nbsp;<l:star/></th>
                <td>
                    <select id="snapshot" class="longField" data-id="snapshot" data-err-id="snapshot">
                        <option>[Latest version]</option>
                    </select>
                    <span class="error option-error option-error_snapshot"></span>
                </td>
            </tr>


            <tr class="hidden cloneOptionsRow">
                <th>Folder for clones:&nbsp;<l:star/></th>
                <td>
                    <select id="cloneFolder" class="longField" data-id="folder" data-err-id="folder"></select>
                    <span class="error option-error option-error_folder"></span>
                </td>
            </tr>

            <tr class="hidden cloneOptionsRow">
                <th>Resource pool:&nbsp;<l:star/></th>
                <td>
                    <select id="resourcePool" class="longField" data-id="pool" data-err-id="pool"></select>
                    <span class="error option-error option-error_pool"></span>
                </td>
            </tr>
            <tr class="hidden cloneOptionsRow">
                <th>Max number of instances:&nbsp;<l:star/></th>
                <td>
                    <div>
                        <input type="text" id="maxInstances" value="1" class="longField" data-id="maxInstances" data-err-id="maxInstances"/>
                    </div>
                    <span class="error option-error option-error_maxInstances"></span>
                </td>
            </tr>
        </table>
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
                    '${cons.imagesData}',
                    '${cons.serverUrl}');
        },
        cache: true
    });
</script>
<table class="runnerFormTable">