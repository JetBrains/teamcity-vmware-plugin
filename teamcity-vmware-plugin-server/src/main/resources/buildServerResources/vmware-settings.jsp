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
            <th class="name">Image name</th>
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
            <th><label for="image">Source:</label></th>
            <td>
                <div>
                    <select name="_image" id="image" data-err-id="image" class="longField"></select>
                </div>
                <span class="error option-error option-error_image"></span>
            </td>
        </tr>

            <tr>
                <th>Select an image type:</th>
                <td>
                    <input type="radio" id="cloneBehaviour_CLONE" name="cloneBehaviour" value="FRESH_CLONE" class="cloneBehaviourRadio"/>
                    <label for="cloneBehaviour_CLONE">Fresh clone</label>
                    <br/>
                    <input type="radio" id="cloneBehaviour_ON_DEMAND_CLONE" name="cloneBehaviour" value="ON_DEMAND_CLONE" class="cloneBehaviourRadio"/>
                    <label for="cloneBehaviour_ON_DEMAND_CLONE">On demand clone</label>
                    <br/>
                    <input type="radio" id="cloneBehaviour_START" name="cloneBehaviour" value="START_STOP" class="cloneBehaviourRadio"/>
                    <label for="cloneBehaviour_START">Start/Stop instance</label>
                    <br/>
                </td>
            </tr>

            <tr class="hidden cloneOptionsRow"  id="tr_snapshot_name">
                <th><label for="snapshot">Snapshot name:</label></th>
                <td>
                    <select id="snapshot" data-err-id="snapshot" class="longField">
                        <option>[Latest version]</option>
                    </select>
                    <span class="error option-error option-error_snapshot"></span>
                </td>
            </tr>


            <tr class="hidden cloneOptionsRow">
                <th>
                    <label for="cloneFolder">Folder for clones</label>
                </th>
                <td>
                    <select id="cloneFolder" data-err-id="folder" class="longField"></select>
                    <span class="error option-error option-error_folder"></span>
                </td>
            </tr>

            <tr class="hidden cloneOptionsRow">
                <th>
                    <label for="resourcePool">Resource pool</label>
                </th>
                <td>
                    <select id="resourcePool" data-err-id="pool" class="longField"></select>
                    <span class="error option-error option-error_pool"></span>
                </td>
            </tr>
            <tr class="hidden cloneOptionsRow">
                <th>
                    <label for="maxInstances">Max number of instances</label>
                </th>
                <td>
                    <div>
                        <input type="text" id="maxInstances" value="1" class="longField"/>
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