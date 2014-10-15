BS = BS || {};
BS.Clouds = BS.Clouds || {};
BS.Clouds.VMWareVSphere = BS.Clouds.VMWareVSphere || {
    _dataKeys: [ 'vmName', 'snapshotName', 'cloneFolder', 'resourcePool', 'cloneBehaviour', 'maxInstances'],
    selectors: {
        imagesSelect: '#image',
        cloneBehaviourRadio: ".cloneBehaviourRadio",
        cloneOptionsRow: '.cloneOptionsRow',
        rmImageLink: '.removeVmImageLink',
        editImageLink: '.editVmImageLink',
        imagesTableRow: '.imagesTableRow'
    },
    init: function (refreshOptionsUrl, refreshSnapshotsUrl, imagesDataElemId, serverUrlElemId) {
        this.refreshOptionsUrl = refreshOptionsUrl;
        this.refreshSnapshotsUrl = refreshSnapshotsUrl;
        this.$imagesDataElem = $j('#' + imagesDataElemId);
        this.selectors.serverUrl = '#' + serverUrlElemId;

        this.$fetchOptionsButton = $j('#vmwareFetchOptionsButton');
        this.$emptyImagesListMessage = $j('.emptyImagesListMessage');
        this.$imagesTableWrapper = $j('.imagesTableWrapper');
        this.$imagesTable = $j("#vmwareImagesTable");
        this.$options = $j('.vmWareSphereOptions');
        this.$image = $j(this.selectors.imagesSelect);
        this.$snapshot = $j('#snapshot');
        this.$cloneFolder = $j("#cloneFolder");
        this.$resourcePool = $j("#resourcePool");
        this.$maxInstances = $j("#maxInstances");
        this.$dialogSubmitButton = $j('#vmwareAddImageButton');
        this.$fetchOptionsError = $j("#error_fetch_options");
        this.$cancelButton = $j('#vmwareCancelAddImageButton');
        this.$showDialogButton = $j('#vmwareShowDialogButton');
        this.loaders = {
            fetchOptions: $j('.fetchingServerOptions'),
            fetchSnapshots: $j('.fetchingSnapshots')
        };

        this.selectors.activeCloneBehaviour = this.selectors.cloneBehaviourRadio + ':checked';

        this._lastImageId = this._imagesDataLength = 0;
        this._toggleDialogShowButton();
        this._initImagesData();
        this._initTemplates();
        this._bindHandlers();
        this.fetchOptions();
        this.renderImagesTable();
    },
    _fetchOptionsInProgress: function () {
        return this.fetchOptionsDeferred ?
            this.fetchOptionsDeferred.state() === 'pending' :
            false;
    },
    fetchOptions: function () {
        var $loader = $j('<div style="padding: 4px 0 5px;">&nbsp;Fetching options...</div>').prepend($j(BS.loadingIcon).clone());

        if ( this._fetchOptionsInProgress() || !this.validateServerSettings()) {
            return false;
        }

        this.fetchOptionsDeferred = $j.Deferred()
            .done(function (response) {
                var $response = $j(response.responseXML),
                    $vms = $response.find('VirtualMachines:eq(0) VirtualMachine'),
                    $pools = $response.find('ResourcePools:eq(0) ResourcePool'),
                    $folders = $response.find('Folders:eq(0) Folder');

                if ($vms.length) {
                    this.fillOptions($vms, $pools, $folders);
                    this._toggleDialogSubmitButton(true);
                    this._toggleDialogShowButton(true);
                }

                this.validateImages($response);
            }.bind(this))
            .fail(function (errorText) {
                this.addError("Unable to fetch options: " + errorText);
                BS.VMWareImageDialog.close();
            }.bind(this))
            .always(function () {
                $loader.remove();
                this._toggleFetchOptionsButton(true);
                this._toggleLoadingMessage('fetchOptions');
            }.bind(this));

        this._toggleFetchOptionsButton();
        this._toggleDialogSubmitButton();
        this._toggleLoadingMessage('fetchOptions', true);
        $loader.insertAfter(this.$fetchOptionsButton);

        BS.ajaxRequest(this.refreshOptionsUrl, {
            parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
            onFailure: function (response) {
                this.fetchOptionsDeferred.reject(response.getStatusText());
            }.bind(this),
            onSuccess: function (response) {
                var $response = $j(response.responseXML),
                    $errors = $response.find("errors:eq(0) error");

                if ($errors.length) {
                    this.fetchOptionsDeferred.reject($errors.text());
                } else {
                    this.fetchOptionsDeferred.resolve(response);
                }
            }.bind(this)
        });

        return false; // to prevent link with href='#' to scroll to the top of the page
    },
    renderImagesTable: function () {
        if (this._imagesDataLength) {
            Object.keys(this.imagesData).forEach(function (imageId) {
                this._renderImageRow(this.imagesData[imageId], imageId);
            }.bind(this));
        }
        this._toggleImagesTable();
    },
    fillOptions: function ($vms, $pools, $folders) {
        this
            ._displayImagesSelect($vms)
            ._displayPoolsSelect($pools)
            ._displayFoldersSelect($folders);
    },
    /**
     * Validates server URL and displays error if URL seems to be incorrect
     */
    validateServerSettings: function () {
        var url = $j(this.selectors.serverUrl).val(),
            isValid = (/^https:\/\/.*\/sdk$/).test(url);

        this.clearErrors();

        if (url.length && !isValid) {
            this.addError("Server URL doesn't seem to be correct. <br/>" +
            "Correct URL should look like this: <strong>https://vcenter/sdk</strong>");
        }

        return isValid;
    },
    validateOptions: function (options) {
        var maxInstances = this.$maxInstances.val(),
            isValid = true;

        // checking properties
        this.clearOptionsErrors(options);

        if ( ! this.$image.val()) {
            this.addOptionError("Please select a VM", "image");
            isValid = false;
        }

        if (!$j.isNumeric(maxInstances) || maxInstances < 1) {
            this.addOptionError("Must be positive number", "instances");
            isValid = false;
        }

        if (this._isClone()) {
            if (!this.$cloneFolder.val()) {
                this.addOptionError("Please select folder", "folder");
                isValid = false;
            }
            if (!this.$resourcePool.val()) {
                this.addOptionError("Please select pool", "pool");
                isValid = false;
            }
        }

        return isValid;
    },
    addImage: function () {
        var newImageId = this._lastImageId++,
            newImage = this._collectImageData();

        this._renderImageRow(newImage, newImageId);
        this.imagesData[newImageId] = newImage;
        this._imagesDataLength += 1;
        this.saveImagesData();
        this._toggleImagesTable();

        return false; // to prevent link with href='#' to scroll to the top of the page
    },
    /**
     * gathers image data from dialog markup
     */
    _collectImageData: function () {
        return  {
            vmName: this.$image.val(),
            snapshotName: this._visibleValue(this.$snapshot) || '[Latest Version]',
            cloneFolder: this._visibleValue(this.$cloneFolder),
            resourcePool: this._visibleValue(this.$resourcePool),
            cloneBehaviour: $j(this.selectors.activeCloneBehaviour).val(),
            maxInstances: this._visibleValue(this.$maxInstances) || '1'
        };
    },
    removeImage: function ($elem) {
        delete this.imagesData[$elem.data('imageId')];
        this._imagesDataLength -= 1;
        $elem.parents(this.selectors.imagesTableRow).remove();
        this.saveImagesData();
        this._toggleImagesTable();
    },
    editImage: function (id) {
        this.imagesData[id] = this._collectImageData();
        this.saveImagesData();
        this.$imagesTable.find(this.selectors.imagesTableRow).remove();
        this.renderImagesTable();
    },
    showEditDialog: function ($elem) {
        var imageId = $elem.data('imageId');

        this.showDialog('edit', imageId);

        this.fetchOptionsDeferred.then(function () {
            var image = this.imagesData[imageId];

            this.$options.find(this.selectors.cloneBehaviourRadio).trigger('change', image.cloneBehaviour);
            this.$cloneFolder.trigger('change', image.cloneFolder);
            this.$resourcePool.trigger('change', image.resourcePool);
            this.$maxInstances.trigger('change', image.maxInstances);

            this.$image.trigger('change', image.vmName);
            this.fetchSnapshotsDeferred
                .then(function () {
                    this.$snapshot.trigger('change', image.snapshotName === '[Latest Version]' ? '' : image.snapshotName);
                }.bind(this));
        }.bind(this));

    },
    showDialog: function (action, imageId) {
        action = action ? 'Edit' : 'Add';
        this.clearOptionsErrors();
        $j('#VMWareImageDialogTitle').text(action + ' Image');

        this.$dialogSubmitButton.val(action).data('imageId', imageId);

        BS.VMWareImageDialog.showCentered();
    },
    saveImagesData: function () {
        var data = Object.keys(this.imagesData).map(function (imageId) {
            return this._dataKeys.map(function (key) {
                return this.imagesData[imageId][key];
            }.bind(this)).join(';').replace(/\[Latest version]/g, '');
        }.bind(this)).join(';X;:');

        if (data.length) {
            data+= ';X;:';
        }

        this.$imagesDataElem.val(data);
    },
    _fetchSnapshotsInProgress: function () {
        return this.fetchSnapshotsDeferred ?
        this.fetchSnapshotsDeferred.state() === 'pending' :
            false;
    },
    /**
     * fetches snapshot list for selected image
     */
    fetchSnapshots: function () {
        if (this._fetchSnapshotsInProgress()) {
            return false;
        }
        this.fetchSnapshotsDeferred = $j.Deferred();
        $j('#realImageInput').val(this.$image.val());
        this._toggleLoadingMessage('fetchSnapshots', true);
        if (this._isClone()) {
            this._toggleDialogSubmitButton();
        }
        BS.ajaxRequest(this.refreshSnapshotsUrl, {
            parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
            onFailure: function (response) {
                this.fetchSnapshotsDeferred.reject();
            },
            onSuccess: function (response) {
                var $response = $j(response.responseXML);

                this._toggleDialogSubmitButton(true);

                if ($response.length) {
                    this._displaySnapshotSelect($response.find('Snapshots:eq(0) Snapshot'));
                }
                this._toggleLoadingMessage('fetchSnapshots');

                this.fetchSnapshotsDeferred.resolve();
            }.bind(this)
        });
    },
    clearErrors: function (target) {
        (target || this.$fetchOptionsError).empty();
    },
    addError: function (errorHTML, target) {
        (target || this.$fetchOptionsError)
            .append($j("<div>").html(errorHTML));
    },
    addOptionError: function (errorHTML, optionName) {
        this.addError(errorHTML, $j('.option-error_' + optionName));
    },
    /**
     * @param {string[]} [options]
     */
    clearOptionsErrors: function (options) {
        (options || [ 'image', 'snapshot', 'folder', 'pool', 'instances']).forEach(function (optionName) {
            this.clearErrors($j('.option-error_' + optionName));
        }.bind(this));
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
                accumulator[id] = {};
                self._dataKeys.forEach(function(key, index) {
                    accumulator[id][key] = props[index];
                });
                self._imagesDataLength++;
            }
            return accumulator;
        }, {});
    },
    _bindHandlers: function () {
        var self = this;

        //// Click Handlers
        this.$fetchOptionsButton.on('click', this._fetchOptionsClickHandler.bind(this));
        this.$showDialogButton.on('click', function () {
            if (! this.$showDialogButton.attr('disabled')) {
                this.showDialog();
            }
            return false;
        }.bind(this));
        this.$dialogSubmitButton.on('click', this._submitDialogClickHandler.bind(this));
        this.$cancelButton.on('click', function () {
            BS.VMWareImageDialog.close();
            return false;
        });
        this.$imagesTable.on('click', this.selectors.rmImageLink, function () {
            var $this = $j(this),
                id = $this.data('imageId'),
                name = self.imagesData[id].vmName;

            if (confirm('Are you sure you want to remove the image "' + name + '"?')) {
                self.removeImage($this);
            }
            return false;
        });
        this.$imagesTable.on('click', this.selectors.editImageLink, function () {
            self.showEditDialog($j(this));

            return false;
        });

        //// Change Handlers
        // - clone behaviour
        $j(this.selectors.cloneBehaviourRadio).on('change', function (e, val) {
            var $elementsToToggle = $j(this.selectors.cloneOptionsRow);

            if (typeof val !== 'undefined') {
                $j(this.selectors.cloneBehaviourRadio + '[value="' + val + '"]').prop('checked', true);
            }

            $elementsToToggle.toggle(this._isClone());
            BS.VMWareImageDialog.recenterDialog();
        }.bind(this));
        // - image
        this.$options.on('change', this.selectors.imagesSelect, function(e, value) {
            this._validateSelectChange(this.$image, value);

            this.fetchSnapshots();
        }.bind(this));
        // - snapshot
        // - folder
        // - pool
        this.$snapshot.add(this.$cloneFolder).add(this.$resourcePool).on('change', function (e, val) {
            this._validateSelectChange($j(e.target), val);
        }.bind(this));

        this.$image.add(this.$snapshot).on('change', function(e) {
            this.validateOptions([$j(e.target).attr('id')])
        }.bind(this));

        // - instances
        this.$maxInstances.on('change', function (e, val) {
            if (typeof val !== 'undefined') {
                $j(this).val(val);
            }
        });
    },
    _validateSelectChange: function ($elem, value) {
        var errId = $elem.attr('data-err-id');

        if (typeof value !== 'undefined') {
            if (! $elem.find('option[value="' + value + '"]').length) {
                this.addOptionError("The " + errId + " '" + value + "' does not exist", errId);
            } else {
                $elem.val(value);
            }
        }
    },
    _fetchOptionsClickHandler: function () {
        if (this.$fetchOptionsButton.attr('disabled') !== 'true') { // it may be undefined
            this.fetchOptions();
        }

        return false; // to prevent link with href='#' to scroll to the top of the page
    },
    _submitDialogClickHandler: function() {
        if (this.validateOptions()) {
            if (this.$dialogSubmitButton.val().toLowerCase() === 'edit') {
                this.editImage(this.$dialogSubmitButton.data('imageId'));
            } else {
                this.addImage();
            }

            BS.VMWareImageDialog.close();
        }

        return false;
    },
    _renderImageRow: function (rows, id) {
        var $row = this.templates.imagesTableRow.clone().attr('data-image-id', id);

        this._dataKeys.forEach(function (className) {
            $row.find('.' + className).text(rows[className]);
        });
        $row.find(this.selectors.rmImageLink).data('imageId', id);
        $row.find(this.selectors.editImageLink).data('imageId', id);
        this.$imagesTable.append($row);
    },
    _toggleImagesTable: function () {
        var toggle = !!this._imagesDataLength;
        this.$imagesTableWrapper.show();
        this.$emptyImagesListMessage.toggle(!toggle);
        this.$imagesTable.toggle(toggle);
    },
    _displayImagesSelect: function ($vms) {
        var self = this,
            $select = this.templates.imagesSelect.clone(),
            $vmGroup = $select.find(".vmGroup"),
            $templatesGroup = $select.find(".templatesGroup");

        $vms.each(function () {
            self._appendOption($j(this).attr('template') == 'false' ? $vmGroup : $templatesGroup, $j(this).attr('name'));
        });

        this.$image.replaceWith($select);
        this.$image = $select;

        return this;
    },
    _displayPoolsSelect: function ($pools) {
        var self = this;

        this.$resourcePool.children().remove();
        this._appendOption(this.$resourcePool, '', '--Please select pool--');
        $pools.each(function () {
            self._appendOption(self.$resourcePool, $j(this).attr('name'));
        });

        return this;
    },
    _displayFoldersSelect: function ($folders) {
        var self = this;

        this.$cloneFolder.children().remove();
        this._appendOption(this.$cloneFolder, '', '--Please select folder--');
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
    _isClone: function () {
        return $j(this.selectors.activeCloneBehaviour).val() !== 'START';
    },
    _toggleDialogSubmitButton: function (enable) {
        this.$dialogSubmitButton.prop('disabled', !enable);
    },
    _toggleFetchOptionsButton: function (enable) {
        this.$fetchOptionsButton.toggle(enable).prop('disabled', !enable);
    },
    _toggleDialogShowButton: function (enable) {
        this.$showDialogButton.attr('disabled', !enable);
    },
    _toggleLoadingMessage: function (loaderName, show) {
        this.loaders[loaderName][show ? 'removeClass' : 'addClass']('message_hidden');
    },
    _visibleValue: function (elem) {
        return elem.is(":visible") ? elem.val() : '';
    },
    _appendOption: function ($target, value, text, type) {
        $target.append($j('<option>').attr('value', value).text(text || value)).attr('data-type', type);
    },
    _initTemplates: function() {
        // Prototype.js ignores script type when parsing scripts (for refresable),
        // so custom script types do not work.
        // Older IE try to interpret `template` tags, that approach fails too.
        this.templates = {
            imagesTableRow: $j('<tr class="imagesTableRow">\
<td class="imageName"><div class="sourceIcon sourceIcon_unknown">?</div><span class="vmName"></span></td>\
<td class="snapshotName"></td>\
<td class="cloneFolder hidden"></td>\
<td class="resourcePool hidden"></td>\
<td class="cloneBehaviour"></td>\
<td class="maxInstances"></td>\
<td class="edit"><a href="#" class="editVmImageLink">edit</a></td>\
<td class="remove"><a href="#" class="removeVmImageLink">delete</a></td>\
            </tr>'),
            imagesSelect: $j('<select name="prop:_image" id="image" data-err-id="image" class="longField">\
    <option value="">--Please select a VM--</option>\
    <optgroup label="Virtual machines" class="vmGroup"></optgroup>\
    <optgroup label="Templates" class="templatesGroup"></optgroup>\
</select>')
        }
    },
    validateImages: function ($response) {
        var updateIcon = function(imageId, type, title, symbol) {
            var $icon = this.$imagesTable.find(this.selectors.imagesTableRow + '[data-image-id=' + imageId + '] .sourceIcon');

            $icon.removeClass().addClass('sourceIcon').removeAttr('title');

            switch (type) {
                case 'error':
                    $icon
                        .addClass('sourceIcon_error')
                        .text(symbol || '!')
                        .attr('title', title);
                    break;
                case 'info':
                    $icon
                        .text(symbol)
                        .attr('title', title);
                    break;
                default:
                    $icon
                        .addClass('sourceIcon_unknown')
                        .text('?');
            }
        }.bind(this);

        Object.keys(this.imagesData).forEach(function (imageId) {
            var name = this.imagesData[imageId].vmName,
                machine = $response.find('VirtualMachine[name="' + name + '"]');

            if (! machine.length) {
                return updateIcon(imageId, 'error', 'Nonexistent source');
            } else {
                updateIcon(imageId, 'info', machine.attr('template') == 'true' ? 'Template' : 'Image', machine.attr('template') == 'true' ? 'T' : 'I');
            }
        }.bind(this));
    }
};

BS.VMWareImageDialog = OO.extend(BS.AbstractModalDialog, {
    getContainer: function() {
        return $('VMWareImageDialog');
    }
});
