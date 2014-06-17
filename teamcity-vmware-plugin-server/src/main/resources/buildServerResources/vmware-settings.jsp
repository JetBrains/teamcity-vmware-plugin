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

<c:set var="refreshUrl"><c:url value="${refreshablePath}"/></c:set>
<c:set var="refreshSnapshotsUrl"><c:url value="${refreshSnapshotsPath}"/></c:set>

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
      <input type="hidden" id="refreshablePath" value="<c:url value="${refreshablePath}"/>"/>
      <input type="button" value="Fetch options" onclick="vmware_refreshOptions('<c:url value="${refreshablePath}"/>'); return false;"/>
    </td>
  </tr>

<tr>
  <td colspan="2">
    <table id="vmware_images_list">
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


<tr>
  <th><label for="image">Agent images:</label></th>
  <td>
    <c:set var="readSnapshotsOnChange">vmware_readSnapshots('<c:url value="${refreshSnapshotsPath}"/>')</c:set>
    <div>
      <props:selectProperty name="image" onchange="${readSnapshotsOnChange}"/>
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
    <input type="button" value="Add image" onclick="vmware_addImage(); return false;"/>
  </td>
</tr>

