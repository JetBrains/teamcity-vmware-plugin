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

var BS = BS || {};
BS.Clouds = BS.Clouds || {};
BS.Clouds.VMWareVSphere = BS.Clouds.VMWareVSphere || (function () {
    var START_STOP = 'START_STOP',
        FRESH_CLONE = 'FRESH_CLONE',
        ON_DEMAND_CLONE = 'ON_DEMAND_CLONE',
        CURRENT_STATE = '__CURRENT_STATE__',
        LATEST_SNAPSHOT='*';

    return {
        _dataKeys: [ 'sourceVmName', 'snapshot', 'folder', 'pool', 'maxInstances', 'nickname'],
        selectors: {
            imagesSelect: '#image',
            behaviourSwitch: '.behaviour__switch',
            cloneOptionsRow: '.cloneOptionsRow',
            rmImageLink: '.removeVmImageLink',
            editImageLink: '.editVmImageLink',
            imagesTableRow: '.imagesTableRow'
        },
        _displayedErrors: {},
        init: function (refreshOptionsUrl, refreshSnapshotsUrl, configurationHelperUrl, imagesDataElemId, serverUrlElemId) {
            this.refreshOptionsUrl = refreshOptionsUrl;
            this.refreshSnapshotsUrl = refreshSnapshotsUrl;
            this.configurationHelperUrl = configurationHelperUrl;
            this.$imagesDataElem = $j('#' + imagesDataElemId);

            this.$serverUrl = $j(BS.Util.escapeId(serverUrlElemId));
            this.$serverUsername = $j(BS.Util.escapeId('vmware_username'));
            this.$serverPassword = $j(BS.Util.escapeId('secure:vmware_password'));
            this.$profileInstancesLimit = $j('#vmware_profile_instance_limit');

            this.$fetchOptionsButton = $j('#vmwareFetchOptionsButton');
            this.$emptyImagesListMessage = $j('.emptyImagesListMessage');
            this.$imagesTableWrapper = $j('.imagesTableWrapper');
            this.$imagesTable = $j('#vmwareImagesTable');
            this.$options = $j('.vmWareSphereOptions');

            this.$image = $j(this.selectors.imagesSelect);
            this.$behaviour = $j('.behaviour__value');
            this.$snapshot = $j('#snapshot');
            this.$currentStateWarning = $j('.currentStateWarning');
            this.$cloneFolder = $j('#cloneFolder');
            this.$resourcePool = $j('#resourcePool');
            this.$maxInstances = $j('#maxInstances');
            this.$agentPool = $j("#agent_pool_id");
            this.$nickname = $j("#nickname");
            this.$cloneOptions = $j(this.selectors.cloneOptionsRow);
            this.$customizationSpec = $j('#customizationSpec');

            this.$addImageButton = $j('#vmwareAddImageButton');
            this.$fetchOptionsError = $j('#error_fetch_options');
            this.$cancelButton = $j('#vmwareCancelAddImageButton');
            this.$showDialogButton = $j('#vmwareShowDialogButton');
            this.loaders = {
                fetchOptions: $j('.fetchingServerOptions'),
                fetchSnapshots: $j('.fetchingSnapshots')
            };

            this._lastImageId = this._imagesDataLength = 0;
            this._initImage();

            this.$cloneOptions.toggleClass('hidden', !this._isClone());
            this._displaySnapshotSelect();

            this._toggleDialogShowButton();
            this._toggleEditLinks();
            this.validateServerSettings() && this.fetchOptions();
            this._initImagesData();
            this._bindHandlers();
            this._disableCheckModifiedForDialog();
            this.renderImagesTable();
        },
        _disableCheckModifiedForDialog: function(){
          $j('.paramsTable input').
              add($j('#realImageInput')).
              add($j('.paramsTable select')).addClass('ignoreModified');
        },
        _fetchOptionsInProgress: function () {
            return this.fetchOptionsDeferred ?
            this.fetchOptionsDeferred.state() === 'pending' :
                false;
        },
        fetchOptions: function () {
            var $loader = $j('.options-loader');

            if ( this._fetchOptionsInProgress() || !this.validateServerSettings()) {
                return false;
            }

            this.fetchOptionsDeferred = $j.Deferred()
                .done(function (response) {
                    var $response = $j(response.responseXML),
                        $vms = $response.find('VirtualMachines:eq(0) VirtualMachine'),
                        $pools = $response.find('ResourcePools:eq(0) ResourcePool'),
                        $custSpecs = $response.find('CustomizationSpecs:eq(0) CustomizationSpec'),
                        $folders = $response.find('Folders:eq(0) Folder');

                    this.$response = $response;

                    if ($vms.length) {
                        this.fillOptions($vms, $pools, $folders, $custSpecs);
                        this._toggleDialogSubmitButton(true);
                        this._toggleDialogShowButton(true);
                        this._toggleEditLinks(true);
                    }

                    this.validateImages();

                    return response;
                }.bind(this))
                .fail(function (errorText) {
                    this.addError('Unable to fetch options: ' + errorText);
                    this.fillOptions([], [], [], []);
                    this._displaySnapshotSelect([]);
                    BS.VMWareImageDialog.close();
                    return errorText;
                }.bind(this))
                .always(function () {
                    $loader.addClass('hidden');
                    this._toggleFetchOptionsButton(true);
                    this._toggleLoadingMessage('fetchOptions');
                }.bind(this));

            this._toggleFetchOptionsButton();
            this._toggleDialogSubmitButton();
            this._toggleLoadingMessage('fetchOptions', true);
            $loader.removeClass('hidden');

            BS.ajaxRequest(this.refreshOptionsUrl, {
                parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
                onFailure: function (response) {
                    this.fetchOptionsDeferred.reject(response.getStatusText());
                }.bind(this),
                onSuccess: function (response) {
                    var $response = $j(response.responseXML),
                        $errors = $response.find('errors:eq(0) error');

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
            BS.Clouds.Admin.CreateProfileForm.checkIfModified();
        },
        fillOptions: function ($vms, $pools, $folders, $custSpecs) {
            this
                ._displayImagesSelect($vms)
                ._displayPoolsSelect($pools)
                ._displayFoldersSelect($folders)
                ._displayCustomizationSpecSelect($custSpecs);
        },
        /**
         * Validates server URL and displays error if URL seems to be incorrect
         * @param {boolean} displayErrors
         */
        validateServerSettings: function (highlightErrors) {
            var isValid = true,
                checkRequired = function ($elem) {
                    var val = $elem.val();
                    if (val != null && ! $elem.val().length) {
                        isValid = false;
                        var id = $elem.attr("id");
                        if (highlightErrors) {
                           var escapedId = BS.Util.escapeId("error_" + id);
                           $j(escapedId).show();
                           $j(escapedId).text('Value is required');
                        }
                    }
                };

            // $j('#newProfileForm .runnerFormTable .error').hide();
            this.clearErrors();

            [ this.$serverUrl, this.$serverUsername, this.$serverPassword ].forEach(checkRequired);

            return isValid;
        },
        addImage: function () {
            var newImageId = this._lastImageId++,
                newImage = this._image;
            this.setupSourceId(this._image);
            this._renderImageRow(newImage, newImageId);
            this.imagesData[newImageId] = newImage;
            this._imagesDataLength += 1;
            this.saveImagesData();
            this._toggleImagesTable();
        },
        editImage: function (id) {
            this.setupSourceId(this._image);
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
        setupSourceId: function(image){
          var nickname = $j.trim(image.nickname);
            if (nickname != ''){
                image['source-id'] = nickname;
            } else {
                image['source-id'] = image.sourceVmName;
            }
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

            BS.Hider.addHideFunction('VMWareImageDialog', this.resetDataAndDialog.bind(this));

            typeof imageId !== 'undefined' && (this._image = $j.extend({}, this.imagesData[imageId]));
            this.$addImageButton.val(action ? 'Save' : 'Add').data('image-id', imageId);
            if (imageId === 'undefined'){
                this.$addImageButton.removeData('image-id');
            }

            BS.VMWareImageDialog.showCentered();
        },
        saveImagesData: function () {
            var imageData = Object.keys(this.imagesData).reduce(function (accumulator, id) {
                var _val = $j.extend({}, this.imagesData[id]);

                delete _val.$image;
                accumulator.push(_val);

                return accumulator;
            }.bind(this), []);

            this.$imagesDataElem.val(JSON.stringify(imageData));
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
            var _image = this.$image.val();

            if (! _image || this._fetchSnapshotsInProgress()) {
                return false;
            }

            $j('#realImageInput').val(_image);
            this.fetchSnapshotsDeferred = $j.Deferred()
                .done(function (response) {
                    var $response = $j(response.responseXML);

                    this._toggleDialogSubmitButton(true);

                    if ($response.length) {
                        this._displaySnapshotSelect($response.find('Snapshots:eq(0) Snapshot'));
                    }
                    this._toggleLoadingMessage('fetchSnapshots');

                    return response;
                }.bind(this))
                .fail(function (response) {
                    BS.Log.error(response);
                });
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
                    this.fetchSnapshotsDeferred.resolve(response);
                }.bind(this)
            });
        },
        checkHelper: function(fieldId, fieldValue, responseElementId) {
          $j('#helperFieldValue').val(fieldValue);
          $j('#helperFieldId').val(fieldId);
          var that = this;
          BS.ajaxRequest(this.configurationHelperUrl, {
              parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
              onSuccess: function(response){
                var $response = $j(response.responseXML);
                if ($response.length){
                    var find = $response.find("fieldValid");
                    if (find.text() == 'false'){
                      var errorCode = $response.find("errorCode");
                      that.addOptionError(errorCode.text(), responseElementId);
                    } else {
                      that.clearErrors(responseElementId);
                    }
                }
              }
            });
        },
        clearErrors: function (errorId) {
            var target = errorId ? $j('.option-error_' + errorId) : this.$fetchOptionsError;

            if (errorId) {
                delete this._displayedErrors[errorId];
            }

            target.empty();
        },
        addError: function (errorHTML, target) {
            (target || this.$fetchOptionsError)
                .append($j('<div>').text(errorHTML));
        },
        addOptionError: function (errorKey, optionName) {
            var html;

            if (errorKey && optionName) {
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
                    this._displayedErrors[optionName].push(errorKey);
                    this.addError(html, $j('.option-error_' + optionName));
                }
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
                rawImagesData = this.$imagesDataElem.val() || '[]',
                imagesData;

            try {
                imagesData = JSON.parse(rawImagesData);
            } catch (e) {
                imagesData = [];
                BS.Log.error('Bad images data: ' + rawImagesData);
            }
            this.imagesData = imagesData.reduce(function (accumulator, imageDataStr) {
                // drop images without sourceVmName
                if (imageDataStr.sourceVmName) {
                    accumulator[self._lastImageId++] = imageDataStr;
                    self._imagesDataLength++;
                }

                return accumulator;
            }, {});
            this.fetchOptionsDeferred && this.fetchOptionsDeferred.done(function () {
                Object.keys(this.imagesData).forEach(function (i, key) {
                    this.imagesData[key].$image = this._getSourceByName(this.imagesData[key].sourceVmName);
                }.bind(this))
            }.bind(this));
        },
        _bindHandlers: function () {
            var self = this;

            //// Click Handlers
            this.$fetchOptionsButton.on('click', this._fetchOptionsClickHandler.bind(this));
            this.$showDialogButton.on('click', this._showDialogClickHandler.bind(this));
            this.$addImageButton.on('click', this._submitDialogClickHandler.bind(this));
            this.$cancelButton.on('click', this._cancelDialogClickHandler.bind(this));
            this.$imagesTable.on('click', this.selectors.rmImageLink, function () {
                var $this = $j(this),
                    id = $this.data('image-id'),
                    name = self.imagesData[id].sourceVmName;

                if (confirm('Are you sure you want to remove the image "' + name + '"?')) {
                    self.removeImage($this);
                }
                return false;
            });
            var editDelegates = this.selectors.imagesTableRow + ' .highlight, ' + this.selectors.editImageLink;
            var that = this;
            this.$imagesTable.on('click', editDelegates, function () {
                if (!that.$addImageButton.prop('disabled')) {
                    self.showEditDialog($j(this));
                }
                return false;
            });

            //// Change Handlers
            // - image
            this.$options.on('change', this.selectors.imagesSelect, function(e, value) {
                if (arguments.length === 1) { // native change by user
                    this._image.sourceVmName = e.target.value;
                    this._image.$image = this._getSourceByName(e.target.value);
                    delete this._image.snapshot;
                } else {
                    this._tryToUpdateSelect(this.$image, value);
                }
              if (!!this._image.$image && this._image.$image.length == 1){
                this._image.$datacenterId = $j(this._image.$image[0]).attr("datacenterId");
                this._filterPoolsAndFolders(this._image.$datacenterId);
              }

                if (this._isClone()) {
                    this.fetchSnapshots();
                }

                $j('#cloneBehaviour_' + START_STOP)
                    .prop('disabled', this._isTemplate())
                    .siblings('label').toggleClass('disabled', this._isTemplate());
                this.validateOptions(e.target.getAttribute('data-id'));
            }.bind(this));
            // - clone behaviour
            // hidden input, triggered from JS only
            this.$behaviour.on('change', function (e, value) {
                var startStop = $j('#cloneBehaviour_' + START_STOP),
                    freshClone = $j('#cloneBehaviour_' + FRESH_CLONE),
                    onDemandClone = $j('#cloneBehaviour_' + ON_DEMAND_CLONE);

                if (arguments.length === 1) {
                    if (startStop.is(':checked')) {
                        this._image.behaviour = startStop.val();
                    } else if (freshClone.is(':checked')) { // FRESH_CLONE is checked if START_STOP is not
                        this._image.behaviour = freshClone.val();
                    } else if (onDemandClone.is(':checked')){
                        this._image.behaviour = onDemandClone.val();
                    }
                } else {
                    $j(this.selectors.behaviourSwitch).prop('checked', false);
                    $j('#cloneBehaviour_' + value).prop('checked', true);
                    this._image.behaviour = value;
                }
                if (this._image.behaviour == startStop.val()){
                    this._image.nickname = '';
                }
                this.$cloneOptions.toggleClass('hidden', !this._isClone());
                if (this._isClone()) {
                    this.fetchSnapshots();
                    this._image.snapshot && this.fetchSnapshotsDeferred
                        .then(function () {
                            this.$snapshot.trigger('change', this._image.snapshot);
                        }.bind(this));
                    !this._image.maxInstances && this.$maxInstances.trigger('change', this._image.maxInstances = 1);
                }

                this.clearErrors(e.target.getAttribute('data-err-id'));

                this.validateOptions(e.target.getAttribute('data-id'));
            }.bind(this));
            $j(this.selectors.behaviourSwitch).on('change', function (e) {
                this.$behaviour.trigger('change', e.target.value);
            }.bind(this));
            // - snapshot
            this.$snapshot.on('change', function(e, value) {
                if (arguments.length === 1) {
                    this._image.snapshot = this.$snapshot.val();
                } else {
                    this._tryToUpdateSelect(this.$snapshot, value);
                }
                this.$currentStateWarning.toggleClass('hidden', this._image.snapshot !== CURRENT_STATE);
                this.validateOptions(e.target.getAttribute('data-id'));
            }.bind(this));

            this.$resourcePool.on('change', function(e, value){
              this.checkHelper('respool', this.$resourcePool.val(), 'pool');
            }.bind(this));

            this.$cloneFolder.on('change', function(e, value){
              this.checkHelper('folder', this.$cloneFolder.val(), 'folder');
            }.bind(this));

            this.$cloneFolder
                .add(this.$resourcePool)
                .add(this.$customizationSpec)
                .add(this.$agentPool)
                .on('change', function (e, value) {
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
            this.$nickname.on('change', function (e, value) {
                if (arguments.length === 1) {
                    this._image.nickname = this.$nickname.val();
                } else {
                    this.$nickname.val(value);
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
                this.validateServerSettings(true) && this.fetchOptions();
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
            BS.VMWareImageDialog.close();

            return false;
        },
        _submitDialogClickHandler: function() {
            if (this.validateOptions()) {

                if (this._image.behaviour === START_STOP) {
                    delete this._image.snapshot;
                    delete this._image.pool;
                    delete this._image.folder;
                    delete this._image.maxInstances;
                }

                if (this.$addImageButton.val().toLowerCase() === 'save') {
                    this.editImage(this.$addImageButton.data('image-id'));
                } else {
                    this.addImage();
                }

                this.validateImages();
                BS.VMWareImageDialog.close();
            }

            return false;
        },
        _renderImageRow: function (props, id) {
            var $row = this.templates.imagesTableRow.clone().attr('data-image-id', id),
                behaviourTexts = {};

            this._dataKeys.forEach(function (className) {
                $row.find('.' + className).text(props[className]);
            });

            behaviourTexts[START_STOP] = 'Start/Stop';
            behaviourTexts[ON_DEMAND_CLONE] = 'Clone';
            behaviourTexts[FRESH_CLONE] = 'Clone; Delete';

            if (props.snapshot === CURRENT_STATE) {
                $row.find('.snapshot').text('"Current state"');
            } else if (props.snapshot == LATEST_SNAPSHOT){
                $row.find('.snapshot').text('"Latest snapshot"');
            }

            if (props.nickname && props.nickname != ''){
                $row.find('.sourceVmName').text(' (' +props.sourceVmName + ")");
            }

            $row.find('.behaviour').text(behaviourTexts[props.behaviour]);
            $row.find(this.selectors.rmImageLink).data('image-id', id);
            $row.find(this.selectors.editImageLink).data('image-id', id);
            this.$imagesTable.append($row);
        },
        _toggleImagesTable: function () {
            var toggle = !!this._imagesDataLength;
            this.$imagesTableWrapper.removeClass('hidden');
            this.$emptyImagesListMessage.toggleClass('hidden', toggle);
            this.$imagesTable.toggleClass('hidden', !toggle);
        },
        _displayImagesSelect: function ($vms) {
            var self = this,
                $select = this.templates.imagesSelect.clone(),
                $vmGroup = $select.find('.vmGroup'),
                $templatesGroup = $select.find('.templatesGroup');

            $vms.each(function () {
                self._appendOption(self._isTemplate($j(this))? $templatesGroup : $vmGroup, $j(this).attr('name'));
            });

            this.$image.replaceWith($select);
            this.$image = $select;
            this.$image.addClass('ignoreModified');

            return this;
        },
        _displayPoolsSelect: function ($pools) {
            var self = this;

            this.$resourcePool.children().remove();
            this._appendOption(this.$resourcePool, '', '--Please select pool--');
            $pools.each(function () {
                self._appendOption(self.$resourcePool, $j(this).attr('value'), $j(this).attr('name'));
            });

            return this;
        },
        _displayCustomizationSpecSelect: function($specs){
            var self = this;

            this.$customizationSpec.children().remove();
            this._appendOption(this.$customizationSpec, '', '<No customization>');
            $specs.each(function () {
                self._appendOption(self.$customizationSpec, $j(this).attr('name'), $j(this).attr('name'));
            });
            return this;
        },
        _displayFoldersSelect: function ($folders) {
            var self = this;

            this.$cloneFolder.children().remove();
            this._appendOption(this.$cloneFolder, '', '--Please select folder--');
            $folders.each(function () {
                self._appendOption(self.$cloneFolder, $j(this).attr('value'), $j(this).attr('name'));
            });

            return this;
        },
        _displaySnapshotSelect: function ($snapshots) {
            var self = this;

            this.$snapshot.children().remove();
            this._appendOption(this.$snapshot, '', '--Please select snapshot--');

            $snapshots && $snapshots.each(function () {
                self._appendOption(self.$snapshot, $j(this).attr('value'), $j(this).attr('name'));
            });
            BS.enableJQueryDropDownFilter(this.$snapshot.attr('id'));
        },
        _isClone: function () {
            return !!(this._image.behaviour && this._image.behaviour !== START_STOP);
        },
        _toggleEditLinks: function (enable) {
            $j(this.selectors.editImageLink).toggleClass('hidden', !enable);
            $j(this.selectors.editImageLink + '_disabled').toggleClass('hidden', !!enable);
        },

        _toggleDialogSubmitButton: function (enable) {
            this.$addImageButton.prop('disabled', !enable);
        },
        _toggleFetchOptionsButton: function (enable) {
            // $fetchOptionsButton is basically an anchor, also attribute allows to add styling
            this.$fetchOptionsButton.attr('disabled', !enable);
        },
        _toggleDialogShowButton: function (enable) {
            // $showDialogButton is basically an anchor, also attribute allows to add styling
            this.$showDialogButton.attr('disabled', !enable);
        },
        _toggleLoadingMessage: function (loaderName, show) {
            this.loaders[loaderName][show ? 'removeClass' : 'addClass']('message_hidden');
        },
        _appendOption: function ($target, value, text, type) {
            $target.append($j('<option>').attr('value', value).text(text || value)).attr('data-type', type);
        },
      /**
       * Makes pools and folders that don't belong to selected datacenter disabled.
       * @param $datacenterId
       * @private
       */
        _filterPoolsAndFolders: function($datacenterId){
          var self = this;
              //$pools = this.$response.find('ResourcePools:eq(0) ResourcePool'),
              //$folders = this.$response.find('Folders:eq(0) Folder');
          // alert($datacenterId);
          this.$cloneFolder.children('option').each(function(){
            var $folder = self.$response.find('Folder[value="' + this.value + '"]');
            var attr = $j($folder).attr('datacenterId');
            $j(this).prop('disabled', !!attr && attr != $datacenterId);
          });
          this.$resourcePool.children('option').each(function(){
            var $pool = self.$response.find('ResourcePool[value="' + this.value + '"]');
            var attr = $j($pool).attr('datacenterId');
            $j(this).prop('disabled', !!attr && attr != $datacenterId);
          });
        },
        // Prototype.js ignores script type when parsing scripts (for refreshable),
        // so custom script types do not work.
        // Older IE try to interpret `template` tags, that approach fails too.
        templates: {
            imagesTableRow: $j('<tr class="imagesTableRow">\
<td class="imageName highlight"><div class="sourceIcon sourceIcon_unknown">?</div><span class="nickname"></span><span class="sourceVmName"></span></td>\
<td class="snapshot highlight"></td>\
<td class="folder hidden highlight"></td>\
<td class="pool hidden highlight"></td>\
<td class="behaviour highlight"></td>\
<td class="maxInstances highlight"></td>\
<td class="edit highlight"><span class="editVmImageLink_disabled" title="Editing is available after successful retrieval of data">edit</span><a href="#" class="editVmImageLink hidden">edit</a></td>\
<td class="remove"><a href="#" class="removeVmImageLink">delete</a></td>\
        </tr>'),
                imagesSelect: $j('<select name="prop:_image" id="image" class="longField" data-id="sourceVmName" data-err-id="sourceVmName">\
<option value="">--Please select a VM--</option>\
<optgroup label="Templates" class="templatesGroup"></optgroup>\
<optgroup label="Virtual machines" class="vmGroup"></optgroup>\
</select>')
        },
        dataDiv: $j('<div class="hidden imagesData"> \
        <input type="hidden" class="imageName">\
        <input type="hidden" class="nickname">\
        <input type="hidden" class="snapshot">\
        <input type="hidden" class="folder">\
        <input type="hidden" class="pool">\
        <input type="hidden" class="behaviour">\
        <input type="hidden" class="maxInstances">\
                     </div>')
        ,
        _errors: {
            badParam: 'Bad parameter',
            required: 'This field cannot be blank',
            templateStart: 'The Start/Stop behaviour cannot be selected for templates',
            nonNegative: 'Must be non-negative number',
            noAccessPool: 'You do not have the privilege "Resource > Assign virtual machine to resource pool" on the selected host/resource pool.',
            noAccessFolder: 'You do not have the privilege "Create from existing virtual machine" on the selected Folder/Datacenter',
            unique: 'There is another source with the same name/nickname',
            nonexistent: 'The %%elem%% &laquo;%%val%%&raquo; does not exist'
        },
        validateOptions: function (options) {
            var maxInstances = this._image.maxInstances,
                isValid = true,
                validators = {
                    sourceVmName: function () {
                        if ( ! this._image.sourceVmName) {
                            this.addOptionError('required', 'sourceVmName');
                            isValid = false;
                        } else {
                            var $machine = this._getSourceByName(this._image.sourceVmName);
                            if (! $machine.length) {
                                this.addOptionError({ key: 'nonexistent', props: { elem: 'source', val: this._image.sourceVmName}}, 'sourceVmName');
                                isValid = false;
                            } else {
                                if (this._isTemplate($machine) && this._image.behaviour === START_STOP) {
                                    this.addOptionError('templateStart', 'behaviour');
                                    isValid = false;
                                }
                            }
                        }
                    }.bind(this),
                    snapshot: function () {
                        if (this._isClone() && !this._image.snapshot) {
                            this.addOptionError('required', 'snapshot');
                            isValid = false;
                        }
                    }.bind(this),
                    pool: function () {
                        if (this._isClone() && !this._image.pool) {
                            this.addOptionError('required', 'pool');
                            isValid = false;
                        }
                    }.bind(this),
                    customizationSpec: function(){

                    },
                    'agent_pool_id': function(){

                    },
                    folder: function () {
                        if (this._isClone() && ! this._image.folder) {
                            this.addOptionError('required', 'folder');
                            isValid = false;
                        }
                    }.bind(this),
                    maxInstances: function () {
                        if (this._isClone() && (! maxInstances || ! $j.isNumeric(maxInstances) || maxInstances < 0 )) {
                            this.addOptionError('nonNegative', 'maxInstances');
                            isValid = false;
                        }
                    }.bind(this),

                    nickname: function() {
                        Object.keys(this.imagesData).forEach(function(imageId){
                            if (imageId == this.$addImageButton.data('image-id'))
                                return;
                            var machine = this.imagesData[imageId];

                            if (this._getSourceId(this._image) == this._getSourceId(machine)){
                                this.addOptionError('unique', 'nickname');
                                isValid = false;
                            }
                        }.bind(this));
                    }.bind(this)
                };

            validators.behaviour = validators.sourceVmName;

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
            var updateIcon = function (imageId, type, title, symbol) {
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
                var machine = this.imagesData[imageId],
                    name = machine.sourceVmName,
                    $machine = this._getSourceByName(name);

                if (! $machine.length) {
                    return updateIcon(imageId, 'error', 'Nonexistent source');
                }

                if (this._isTemplate($machine) && this.imagesData[imageId].behaviour === START_STOP) {
                    return updateIcon(imageId, 'error', this._errors.templateStart);
                }

                if (! machine.behaviour || (machine.behaviour !== START_STOP && ! machine.maxInstances)) {
                    return updateIcon(imageId, 'error', this._errors.badParam);
                }
                updateIcon(imageId, 'info', this._isTemplate($machine) ? 'Template' : 'Machine', this._isTemplate($machine) ? 'T' : 'M');
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
      /**
       * Updates add/edit image dialog, when user clicks add image or edit
       * @private
       */
        _triggerDialogChange: function () {
            var image = this._image;

            this.$image.trigger('change', image.sourceVmName || '');
            BS.enableJQueryDropDownFilter(this.$image.attr('id'), {});

            this.$behaviour.trigger('change', image.behaviour || '');
            this.fetchSnapshotsDeferred && this.fetchSnapshotsDeferred
                .then(function () {
                    this.$snapshot.trigger('change', image.snapshot || '');
                    BS.enableJQueryDropDownFilter(this.$snapshot.attr('id'), {});
                }.bind(this));

            this.$resourcePool.trigger('change', image.pool || '');
            BS.enableJQueryDropDownFilter(this.$resourcePool.attr('id'), {});

            this.$customizationSpec.trigger('change', image.customizationSpec || '');
            BS.enableJQueryDropDownFilter(this.$customizationSpec.attr('id'), {});

            this.$agentPool.trigger('change', image['agent_pool_id'] || '');
            BS.enableJQueryDropDownFilter(this.$agentPool.attr('id'), {});

            this.$cloneFolder.trigger('change', image.folder || '');
            BS.enableJQueryDropDownFilter(this.$cloneFolder.attr('id'), {});

            this.$maxInstances.trigger('change', image.maxInstances || '');
            this.$nickname.trigger('change', image.nickname || '');
        },
        _initImage: function () {
            this._image = {
                behaviour: FRESH_CLONE,
                maxInstances: 1
            };
        },
        _getSourceId: function(image){
            if (image.nickname && image.nickname != ''){
                return image.nickname;
            } else {
                return image.sourceVmName;
            }
        },
        _getSourceByName: function (name) {
            return this.$response.find('VirtualMachine[name="' + name + '"]');
        },
        /**
         * @param {jQuery} [$source]
         * @returns {boolean}
         * @private
         */
        _isTemplate: function ($source) {
            var _source = $source || this._image.$image;

            return (_source && _source.length) ?
                _source.attr('template') == 'true' :
                false;
        }
    }
})();

BS.VMWareImageDialog = OO.extend(BS.AbstractModalDialog, {
    getContainer: function() {
        return $('VMWareImageDialog');
    }
});
