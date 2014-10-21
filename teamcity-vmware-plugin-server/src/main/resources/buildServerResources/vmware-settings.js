/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

BS = BS || {};
BS.Clouds = BS.Clouds || {};
BS.Clouds.VMWareVSphere = BS.Clouds.VMWareVSphere || (function () {
    var START_STOP = 'START_STOP',
        FRESH_CLONE = 'FRESH_CLONE',
        ON_DEMAND_CLONE = 'ON_DEMAND_CLONE';
    
    return {
        _dataKeys: [ 'sourceName', 'snapshot', 'folder', 'pool', 'behaviour', 'maxInstances'],
        selectors: {
            imagesSelect: '#image',
            behaviourSwitch: ".behaviour__switch",
            cloneOptionsRow: '.cloneOptionsRow',
            rmImageLink: '.removeVmImageLink',
            editImageLink: '.editVmImageLink',
            imagesTableRow: '.imagesTableRow'
        },
        _displayedErrors: {},
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
            this.$behaviour = $j('.behaviour__value');
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

            this._lastImageId = this._imagesDataLength = 0;
            this._initImage();
            this._toggleDialogShowButton();
            this._initImagesData();
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

                    this.$response = $response;

                    if ($vms.length) {
                        this.fillOptions($vms, $pools, $folders);
                        this._toggleDialogSubmitButton(true);
                        this._toggleDialogShowButton(true);
                    }

                    this.validateImages();

                    return response;
                }.bind(this))
                .fail(function (errorText) {
                    this.addError("Unable to fetch options: " + errorText);
                    BS.VMWareImageDialog.close();
                    return errorText;
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
        addImage: function () {
            var newImageId = this._lastImageId++,
                newImage = this._image;

            this._renderImageRow(newImage, newImageId);
            this.imagesData[newImageId] = newImage;
            this._imagesDataLength += 1;
            this.saveImagesData();
            this._toggleImagesTable();

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        editImage: function (id) {
            this.imagesData[id] = this._image;
            this.saveImagesData();
            this.$imagesTable.find(this.selectors.imagesTableRow).remove();
            this.renderImagesTable();
        },
        removeImage: function ($elem) {
            delete this.imagesData[$elem.data('image-id')];
            this._imagesDataLength -= 1;
            $elem.parents(this.selectors.imagesTableRow).remove();
            this.saveImagesData();
            this._toggleImagesTable();
        },
        showEditDialog: function ($elem) {
            var imageId = $elem.parents(this.selectors.imagesTableRow).data('image-id');

            this.showDialog('edit', imageId);

            this.fetchOptionsDeferred.then(function () {
                this._triggerDialogChange();
            }.bind(this));
        },
        showDialog: function (action, imageId) {
            $j('#VMWareImageDialogTitle').text((action ? 'Edit' : 'Add') + ' Image');

            this._initImage(); this._displayedErrors = {}; this.clearOptionsErrors(); // fix: 'ESC' closes dialog without calling custom `close`
            typeof imageId !== 'undefined' && (this._image = $j.extend({}, this.imagesData[imageId]));
            this.$dialogSubmitButton.val(action ? 'Save' : 'Add').data('image-id', imageId);

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
                    this.fetchSnapshotsDeferred.reject(response);
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
        clearErrors: function (errorId) {
            var target = errorId ? $j('.option-error_' + errorId) : this.$fetchOptionsError;

            if (errorId) {
                this._displayedErrors[errorId] = [];
            }

            target.empty();
        },
        addError: function (errorHTML, target) {
            (target || this.$fetchOptionsError)
                .append($j("<div>").html(errorHTML));
        },
        addOptionError: function (errorKey, optionName) {
            var html;
            this._displayedErrors[optionName] = this._displayedErrors[optionName] || [];

            if (typeof errorKey !== 'string') {
                html = this._errors[errorKey.key];
                Object.keys(errorKey.props).forEach(function(key) {
                    html = html.replace('%%'+key+'%%', errorKey.props[key]);
                });
                errorKey = errorKey.key;
            } else {
                html = this._errors[errorKey];
            }

            if (this._displayedErrors[optionName].indexOf(errorKey) === -1) {
                this._displayedErrors[optionName].push(errorKey)
                this.addError(html, $j('.option-error_' + optionName));
            }
        },
        /**
         * @param {string[]} [options]
         */
        clearOptionsErrors: function (options) {
            (options || this._dataKeys).forEach(function (optionName) {
                this.clearErrors(optionName);
            }.bind(this));
        },
        _initImagesData: function () {
            var self = this,
                rawImagesData = this.$imagesDataElem.val() || '',
                imagesData = rawImagesData && rawImagesData.split(';X;:') || [],
                namePropIndex = this._dataKeys.indexOf('sourceName');

            this.imagesData = imagesData.reduce(function (accumulator, imageDataStr) {
                var props = imageDataStr.split(';'),
                    id;

                // drop images without name
                if (props[namePropIndex].length) {
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
            this.$showDialogButton.on('click', this._showDialogClickHandler.bind(this));
            this.$dialogSubmitButton.on('click', this._submitDialogClickHandler.bind(this));
            this.$cancelButton.on('click', this._cancelDialogClickHandler.bind(this));
            this.$imagesTable.on('click', this.selectors.rmImageLink, function () {
                var $this = $j(this),
                    id = $this.data('image-id'),
                    name = self.imagesData[id].sourceName;

                if (confirm('Are you sure you want to remove the image "' + name + '"?')) {
                    self.removeImage($this);
                }
                return false;
            });
            var editDelegates = this.selectors.imagesTableRow + ' .highlight, ' + this.selectors.editImageLink;
            this.$imagesTable.on('click', editDelegates, function () {
                self.showEditDialog($j(this));
                return false;
            });

            //// Change Handlers
            // - image
            this.$options.on('change', this.selectors.imagesSelect, function(e, value) {
                if (arguments.length === 1) { // native change by user
                    this._image.sourceName = e.target.value;
                } else {
                    this._tryToUpdateSelect(this.$image, value);
                }

                if (this._isClone()) {
                    this.fetchSnapshots();
                }

                this.validateOptions(e.target.getAttribute('data-id'));
            }.bind(this));
            // - clone behaviour
            this.$behaviour.on('change', function (e, value) {
                var $elementsToToggle = $j(this.selectors.cloneOptionsRow),
                    startStop = $j('#cloneBehaviour_' + START_STOP),
                    freshClone = $j('#cloneBehaviour_' + FRESH_CLONE),
                    onDemandClone = $j('#cloneBehaviour_' + ON_DEMAND_CLONE);

                if (arguments.length === 1) { // triggered by UI
                    freshClone.prop('disabled', startStop.is(':checked'));

                    if (startStop.is(':checked')) {
                        this._image.behaviour = startStop.val();
                    } else if (freshClone.is(':checked')) { // onDemandClone is checked as startStop is not
                        this._image.behaviour = freshClone.val();
                        //onDemandClone.prop('checked', true); // just in case
                    } else if (onDemandClone.is(':checked')){
                        this._image.behaviour = onDemandClone.val();
                    }
                } else {
                    $j(this.selectors.behaviourSwitch).prop('checked', false);
                    $j('#cloneBehaviour_' + value).prop('checked', true);
                    freshClone.prop('disabled', value === START_STOP);

                    if (value === FRESH_CLONE) {
                        onDemandClone.prop('checked', true);
                    }
                }
                this.clearErrors(e.target.getAttribute('data-err-id'));
                $elementsToToggle.toggle(this._isClone());

                if (! this._isClone()) {
                    $elementsToToggle.each(function () {
                        delete self._image[this.getAttribute('data-id')];
                    })
                }

                this.validateOptions(e.target.getAttribute('data-id'));
                BS.VMWareImageDialog.recenterDialog();
            }.bind(this));
            $j(this.selectors.behaviourSwitch).on('change', function () {
                this.$behaviour.trigger('change');
            }.bind(this));
            // - snapshot
            this.$snapshot.on('change', function(e, value) {
                if (arguments.length === 1) {
                    this._image.snapshot = this.$snapshot.val();
                } else {
                    this._tryToUpdateSelect(this.$snapshot, value);
                }
                this.validateOptions(e.target.getAttribute('data-id'));
            }.bind(this));
            // - folder
            // - pool
            this.$cloneFolder.add(this.$resourcePool).on('change', function (e, value) {
                var elem = e.target;

                if (arguments.length === 1) {
                    this._image[elem.getAttribute('data-id')] = elem.value;
                } else {
                    this._tryToUpdateSelect($j(elem), value);
                }
                this.validateOptions(elem.getAttribute('data-id'));
            }.bind(this));

            // - instances
            this.$maxInstances.on('change', function (e, value) {
                if (arguments.length === 1) {
                    this._image.maxInstances = this.$maxInstances.val();
                } else {
                    this.$maxInstances.val(value);
                }
                this.validateOptions(e.target.getAttribute('data-id'));
            }.bind(this));
        },
        /**
         * Tries to update <select> value of given elem with given value
         * Displays error in case value not found (looks into 'data-err-id' attribute
         *  to get error element modifier)
         * @param {jQuery} $elem
         * @param value
         * @private
         */
        _tryToUpdateSelect: function ($elem, value) {
            var errId = $elem.attr('data-err-id');

            if (! $elem.find('option[value="' + value + '"]').length) {
                this.addOptionError({ key: 'nonexistent', props: { elem: errId, val: value}}, errId);
            } else {
                $elem.val(value);
            }
        },
        _fetchOptionsClickHandler: function () {
            if (this.$fetchOptionsButton.attr('disabled') !== 'true') { // it may be undefined
                this.fetchOptions();
            }

            return false; // to prevent link with href='#' to scroll to the top of the page
        },
        _showDialogClickHandler: function () {
            if (! this.$showDialogButton.attr('disabled')) {
                this.showDialog();
            }
            return false;
        },
        _cancelDialogClickHandler: function () {
            return BS.VMWareImageDialog.close();
        },
        _submitDialogClickHandler: function() {
            if (this.validateOptions()) {
                if (this.$dialogSubmitButton.val().toLowerCase() === 'save') {
                    this.editImage(this.$dialogSubmitButton.data('image-id'));
                } else {
                    this.addImage();
                }

                this.validateImages();
                BS.VMWareImageDialog.close();
            }

            return false;
        },
        _renderImageRow: function (rows, id) {
            var $row = this.templates.imagesTableRow.clone().attr('data-image-id', id);

            this._dataKeys.forEach(function (className) {
                $row.find('.' + className).text(rows[className]);
            });
            $row.find(this.selectors.rmImageLink).data('image-id', id);
            $row.find(this.selectors.editImageLink).data('image-id', id);
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
            return !!(this._image.behaviour && this._image.behaviour !== START_STOP);
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
        _appendOption: function ($target, value, text, type) {
            $target.append($j('<option>').attr('value', value).text(text || value)).attr('data-type', type);
        },
        // Prototype.js ignores script type when parsing scripts (for refreshable),
        // so custom script types do not work.
        // Older IE try to interpret `template` tags, that approach fails too.
        templates: {
            imagesTableRow: $j('<tr class="imagesTableRow">\
<td class="imageName highlight"><div class="sourceIcon sourceIcon_unknown">?</div><span class="sourceName"></span></td>\
<td class="snapshot highlight"></td>\
<td class="folder hidden highlight"></td>\
<td class="pool hidden highlight"></td>\
<td class="behaviour highlight"></td>\
<td class="maxInstances highlight"></td>\
<td class="edit highlight"><a href="#" class="editVmImageLink">edit</a></td>\
<td class="remove"><a href="#" class="removeVmImageLink">delete</a></td>\
        </tr>'),
                imagesSelect: $j('<select name="prop:_image" id="image" class="longField" data-id="sourceName" data-err-id="sourceName">\
<option value="">--Please select a VM--</option>\
<optgroup label="Virtual machines" class="vmGroup"></optgroup>\
<optgroup label="Templates" class="templatesGroup"></optgroup>\
</select>')
        },
        _errors: {
            required: 'Required field cannot be left blank',
                templateStart: 'START_STOP behaviour cannot be selected for templates',
                positiveNumber: 'Must be positive number',
                nonexistent: "The %%elem%% &laquo;%%val%%&raquo; does not exist"
        },
        validateOptions: function (options) {
            var maxInstances = this._image.maxInstances,
                isValid = true,
                validators = {
                    sourceName: function () {
                        if ( ! this._image.sourceName) {
                            this.addOptionError('required', "sourceName");
                            isValid = false;
                        } else {
                            var machine = this.$response.find('VirtualMachine[name="' + this._image.sourceName + '"]');
                            if (! machine.length) {
                                this.addOptionError({ key: 'nonexistent', props: { elem: 'source', val: this._image.sourceName}}, "sourceName");
                                isValid = false;
                            } else {
                                if (machine.attr('template') == 'true' && this._image.behaviour === START_STOP) {
                                    this.addOptionError('templateStart', "behaviour");
                                    isValid = false;
                                }
                            }
                        }
                    }.bind(this),
                    snapshot: function () {},
                    pool: function () {
                        if (this._isClone() && !this._image.pool) {
                            this.addOptionError('required', "pool");
                            isValid = false;
                        }
                    }.bind(this),
                    folder: function () {
                        if (this._isClone() && ! this._image.folder) {
                            this.addOptionError('required', "folder");
                            isValid = false;
                        }
                    }.bind(this),
                    maxInstances: function () {
                        if ( ! maxInstances || ! $j.isNumeric(maxInstances) || maxInstances < 1) {
                            this.addOptionError('positiveNumber', "maxInstances");
                            isValid = false;
                        }
                    }.bind(this)
                };

            validators.behaviour = validators.sourceName;

            if (options && ! $j.isArray(options)) {
                options = [options];
            }

            this.clearOptionsErrors(options);

            (options || this._dataKeys).forEach(function(option) {
                validators[option](); // validators are already bound to parent object
            });

            return isValid;
        },
        validateImages: function () {
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
                var name = this.imagesData[imageId].sourceName,
                    machine = this.$response.find('VirtualMachine[name="' + name + '"]');

                if (! machine.length) {
                    return updateIcon(imageId, 'error', 'Nonexistent source');
                }

                if (machine.attr('template') == 'true' && this.imagesData[imageId].behaviour === START_STOP) {
                    return updateIcon(imageId, 'error', this._errors.templateStart);
                }
                updateIcon(imageId, 'info', machine.attr('template') == 'true' ? 'Template' : 'Machine', machine.attr('template') == 'true' ? 'T' : 'M');
            }.bind(this));
        },
        resetDataAndDialog: function () {
            this._initImage();
            this._displayedErrors = {};

            if (this.$response) {
                this._triggerDialogChange();
            }
            this.clearOptionsErrors();
        },
        _triggerDialogChange: function () {
            var image = this._image;

            this.$image.trigger('change', image.sourceName || '');
            this.$behaviour.trigger('change', image.behaviour || '');
            this.fetchSnapshotsDeferred && this.fetchSnapshotsDeferred
                .then(function () {
                    this.$snapshot.trigger('change', image.snapshot === '[Latest Version]' ? '' : image.snapshot || '');
                }.bind(this));
            this.$resourcePool.trigger('change', image.pool || '');
            this.$cloneFolder.trigger('change', image.folder || '');
            this.$maxInstances.trigger('change', image.maxInstances || '');
        },
        _initImage: function () {
            this._image = {
                maxInstances: 1
            };
        }

    }
})();

BS.VMWareImageDialog = OO.extend(BS.AbstractModalDialog, {
    getContainer: function() {
        return $('VMWareImageDialog');
    },
    close: function () {
        BS.Clouds.VMWareVSphere.resetDataAndDialog();
        BS.AbstractModalDialog.close.apply(this);
        return false;
    }
});
