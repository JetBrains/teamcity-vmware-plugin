<jsp:useBean id="cons" class="jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants"/>
<%@ include file="/include.jsp" %>
<script type="text/javascript">

  function vmware_refreshOptions(refreshUrl){

    BS.ajaxRequest(refreshUrl, {
      parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),

      onComplete: function(response){
        var root = BS.Util.documentRoot(response);
        if (!root) return;

        var vms = root.getElementsByTagName("VirtualMachines")[0].getElementsByTagName("VirtualMachine");
        $j('#image').find("option").remove();
        $j('#image').append($j("<option>").val("").text("--Please select a VM--"))
        $j('#image').append($j("<optgroup>").attr("label", "Virtual machines"));
        for (var i=0; i<vms.length; i++) {
          var vm = vms[i];
          var vmName = vm.getAttribute('name');
          var vmTemplate = vm.getAttribute('template');
          if (vmTemplate == 'false') {
            $j("#image optgroup[label='Virtual machines']").append($j("<option>").attr("value",  vmName).text(vmName));
          }
        }
        $j('#image').append($j("<optgroup>").attr("label", "Templates"));
        for (var i=0; i<vms.length; i++) {
          var vm = vms[i];
          var vmName = vm.getAttribute('name');
          var vmTemplate = vm.getAttribute('template');
          if (vmTemplate == 'true') {
            $j("#image optgroup[label='Templates']").append($j("<option>").attr("value",  vmName).text(vmName));
          }
        }

        var pools = root.getElementsByTagName("ResourcePools")[0].getElementsByTagName("ResourcePool");
        $j("#resourcePool").find("option").remove();
        for (var i=0; i<pools.length; i++){
          var pool = pools[i];
          var poolName = pool.getAttribute("name");
          option = document.createElement("option");
          $j('#resourcePool').append("<option value='"+poolName+"'>" + poolName + "</option>");
        }


        var folders = root.getElementsByTagName("Folders")[0].getElementsByTagName("Folder");
        $j("#cloneFolder").find("option").remove();
        for (var i=0; i<folders.length; i++){
          var folder = folders[i];
          var folderName = folder.getAttribute("name");
          $j("#cloneFolder").append("<option value='"+folderName+"'>" + folderName + "</option>");
        }
      }.bind(this)
    });
  }

  function vmware_addImage() {
    var vmName = $j("#image option:selected").text();
    var snapshotName = $j("#snapshot option:selected").text();
    var cloneFolder = $j("#cloneFolder").val();
    var resourcePool = $j("#resourcePool").val();
    var cloneBehaviour = $j("#cloneBehaviour").val();
    var maxInstances = $j("#maxInstances").val();

    // checking properties
    $j("#error_image").empty();
    $j("#error_cloneBehaviour").empty();
    var hadError = false;
    if ($j("#image option:selected").val() == "") {
      $j("#error_image").append($j("<div>").text("Please select a VM"));
      hadError = true;
    }
    if ($j("#image option:selected").parent().attr("label") == 'Templates' && cloneBehaviour == 'START') {
      $j("#error_cloneBehaviour").append($j("<div>").text("Start/Stop mode is not available for readonly(template) VMs"));
      hadError = true;
    }
    if ($j("#snapshot option:selected").val() == "" && cloneBehaviour == "LINKED_CLONE") {
      $j("#error_cloneBehaviour").append($j("<div>").text("Linked clone mode requires an existing snapshot"));
      hadError = true;
    }
    if (!hadError) {
      vmware_addImageInternal(vmName, snapshotName, cloneFolder, resourcePool, cloneBehaviour, maxInstances);
      vmware_updateHidden();
    }
  }

  function vmware_addImageInternal(vmName, snapshotName, cloneFolder, resourcePool, cloneBehaviour, maxInstances){
    $j("#vmware_images_list tbody").append($j("<tr>")
        .append($j("<td>").text(vmName))
        .append($j("<td>").text(snapshotName))
        .append($j("<td>").text(cloneFolder))
        .append($j("<td>").text(resourcePool))
        .append($j("<td>").text(cloneBehaviour))
        .append($j("<td>").text(maxInstances))
        .append($j("<td>").append($j("<a>").attr("href", "#").attr("onclick", "$j(this).closest('tr').remove();vmware_updateHidden();return false;").text("X")))
    );
  }

  function vmware_updateHidden(){
    $j("#${cons.imagesData}").val("");
    var data = "";
    $j("#vmware_images_list tbody tr").each(function(){
      if ($j(this).children("td").size() > 0) {
        $j(this).find("td").each(function () {
          data = data + $j(this).text().replace("[Latest version]", "");
          data = data + ";"
        });

        data = data + ":";
      }
    });
    $j("#${cons.imagesData}").val(data);
  }

  function vmware_readSnapshots(refreshUrl){
    BS.ajaxRequest(refreshUrl, {
      parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
      onSuccess: function(response){
        var root = BS.Util.documentRoot(response);
        if (!root) return;

        var snapshots = root.getElementsByTagName("Snapshots")[0].getElementsByTagName("Snapshot");
        $j("#snapshot").find("option").remove();
        $j("#snapshot").append("<option value=''>[Latest version]</option>");
        for (var i=0; i<snapshots.length; i++){
          var snapshot = snapshots[i];
          var snapName = snapshot.getAttribute("name");
          $j("#snapshot").append("<option value='"+snapName+"'>" + snapName + "</option>");
        }
      }.bind(this)
    });
  }

  function vmware_restoreFromHidden(){
    var partsOfStr = $j("#${cons.imagesData}").val().split(';X;:');
    for (var i=0; i<partsOfStr.length; i++){
      if (partsOfStr.length ==0)
        break;
      var ones = partsOfStr[i].split(';');
      if (ones[0].length > 0) {
        vmware_addImageInternal(ones[0], ones[1], ones[2], ones[3], ones[4], ones[5]);
      }
    }
  }

  vmware_refreshOptions($j("#refreshablePath").val());
  vmware_restoreFromHidden();

</script>