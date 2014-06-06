<jsp:useBean id="cons" class="jetbrains.buildServer.clouds.vmware.web.VMWareWebConstants"/>
<%@ include file="/include.jsp" %>
<script type="text/javascript">

  function refreshVMWareOptions(refreshUrl){
    document.getElementById("temp").value="Trying";
    BS.ajaxRequest(refreshUrl, {
      parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
      onSuccess: function(response){
        var root = BS.Util.documentRoot(response);
        if (!root) return;

        var vms = root.getElementsByTagName("VirtualMachines")[0].getElementsByTagName("VirtualMachine");
        $j('#image').find("option").remove();
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

  function addImage(){
    $j("#vmware_images_list tbody").append($j("<tr>")
        .append($j("<td>").text($j("#image option:selected").text()))
        .append($j("<td>").text($j("#snapshot option:selected").text()))
        .append($j("<td>").text($j("#cloneFolder").val()))
        .append($j("<td>").text($j("#resourcePool").val()))
        .append($j("<td>").text($j("#cloneBehaviour").val()))
        .append($j("<td>").append($j("<a>").attr("href", "#").attr("onclick", "$j(this).closest('tr').remove();updateHidden();return false;").text("X")))
    );

    updateHidden();
  }

  function updateHidden(){
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

  function readSnapshots(refreshUrl){
    document.getElementById("temp").value="Trying Sn";
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

</script>