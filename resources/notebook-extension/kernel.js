define(function(){
    function onload() {
        if (!Element.prototype.scrollIntoViewIfNeeded) {
            Element.prototype.scrollIntoViewIfNeeded = function (centerIfNeeded) {
                centerIfNeeded = arguments.length === 0 ? true : !!centerIfNeeded;

                var parent = this.parentNode,
                    parentComputedStyle = window.getComputedStyle(parent, null),
                    parentBorderTopWidth = parseInt(parentComputedStyle.getPropertyValue('border-top-width')),
                    parentBorderLeftWidth = parseInt(parentComputedStyle.getPropertyValue('border-left-width')),
                    overTop = this.offsetTop - parent.offsetTop < parent.scrollTop,
                    overBottom = (this.offsetTop - parent.offsetTop + this.clientHeight - parentBorderTopWidth) > (parent.scrollTop + parent.clientHeight),
                    overLeft = this.offsetLeft - parent.offsetLeft < parent.scrollLeft,
                    overRight = (this.offsetLeft - parent.offsetLeft + this.clientWidth - parentBorderLeftWidth) > (parent.scrollLeft + parent.clientWidth),
                    alignWithTop = overTop && !overBottom;

                if ((overTop || overBottom) && centerIfNeeded) {
                    parent.scrollTop = this.offsetTop - parent.offsetTop - parent.clientHeight / 2 - parentBorderTopWidth + this.clientHeight / 2;
                }

                if ((overLeft || overRight) && centerIfNeeded) {
                    parent.scrollLeft = this.offsetLeft - parent.offsetLeft - parent.clientWidth / 2 - parentBorderLeftWidth + this.clientWidth / 2;
                }

                if ((overTop || overBottom || overLeft || overRight) && !centerIfNeeded) {
                    this.scrollIntoView(alignWithTop);
                }
            };
        }

        var utils = require('base/js/utils');
        var keyboard = require('base/js/keyboard');
        var $ = require('jquery');
        var CodeMirror = require('codemirror/lib/codemirror');
        var Completer = requirejs("notebook/js/completer").Completer;
        var Cell = requirejs("notebook/js/cell").Cell;
        var CodeCell = requirejs("notebook/js/codecell").CodeCell;

        var cssUrl = require.toUrl(Jupyter.kernelselector.kernelspecs.kotlin.resources["kernel.css"]);
        $('head').append('<link rel="stylesheet" type="text/css" href="' + cssUrl + '">');

        var keycodes = keyboard.keycodes;

        var prepend_n_prc = function(str, n) {
            for( var i =0 ; i< n ; i++){
                str = '%'+str ;
            }
            return str;
        };

        var _existing_completion = function(item, completion_array){
            for( var i=0; i < completion_array.length; i++) {
                if (completion_array[i].trim().substr(-item.length) == item) {
                    return true;
                }
            }
            return false;
        };

        // what is the common start of all completions
        function shared_start(B, drop_prct) {
            if (B.length == 1) {
                return B[0];
            }
            var A = [];
            var common;
            var min_lead_prct = 10;
            for (var i = 0; i < B.length; i++) {
                var str = B[i].str;
                var localmin = 0;
                if(drop_prct === true){
                    while ( str.substr(0, 1) == '%') {
                        localmin = localmin+1;
                        str = str.substring(1);
                    }
                }
                min_lead_prct = Math.min(min_lead_prct, localmin);
                A.push(str);
            }

            if (A.length > 1) {
                var tem1, tem2, s;
                A = A.slice(0).sort();
                tem1 = A[0];
                s = tem1.length;
                tem2 = A.pop();
                while (s && tem2.indexOf(tem1) == -1) {
                    tem1 = tem1.substring(0, --s);
                }
                if (tem1 === "" || tem2.indexOf(tem1) !== 0) {
                    return {
                        replaceText: prepend_n_prc('', min_lead_prct),
                        type: "computed",
                        from: B[0].from,
                        to: B[0].to
                    };
                }
                return {
                    replaceText: prepend_n_prc(tem1, min_lead_prct),
                    type: "computed",
                    from: B[0].from,
                    to: B[0].to
                };
            }
            return null;
        }

        Completer.prototype.startCompletion = function (doAutoPrint) {
            /**
             * call for a 'first' completion, that will set the editor and do some
             * special behavior like autopicking if only one completion available.
             */
            this.do_auto_print = !!doAutoPrint;
            if (this.editor.somethingSelected()|| this.editor.getSelections().length > 1) return;
            this.done = false;
            // use to get focus back on opera
            this.carry_on_completion(true);
        };

        Completer.prototype.carry_on_completion = function (first_invocation) {
            /**
             * Pass true as parameter if you want the completer to autopick when
             * only one completion. This function is automatically reinvoked at
             * each keystroke with first_invocation = false
             */
            var cur = this.editor.getCursor();
            var line = this.editor.getLine(cur.line);
            var pre_cursor = this.editor.getRange({
                line: cur.line,
                ch: cur.ch - 1
            }, cur);

            // we need to check that we are still on a word boundary
            // because while typing the completer is still reinvoking itself
            // so dismiss if we are on a "bad" character
            if (!this.reinvoke(pre_cursor) && !first_invocation) {
                this.close();
                return;
            }

            this.autopick = false;
            if (first_invocation) {
                this.autopick = true;
            }

            // We want a single cursor position.
            if (this.editor.somethingSelected()|| this.editor.getSelections().length > 1) {
                return;
            }

            // one kernel completion came back, finish_completing will be called with the results
            // we fork here and directly call finish completing if kernel is busy
            var cursor_pos = this.editor.indexFromPos(cur);
            var text = this.editor.getValue();
            cursor_pos = utils.js_idx_to_char_idx(cursor_pos, text);
            if (this.skip_kernel_completion) {
                this.finish_completing({ content: {
                        matches: [],
                        cursor_start: cursor_pos,
                        cursor_end: cursor_pos,
                    }});
            } else {
                this.cell.kernel.complete(text, cursor_pos,
                    $.proxy(this.finish_completing, this)
                );
            }
        };

        Completer.prototype.finish_completing = function (msg) {
            /**
             * let's build a function that wrap all that stuff into what is needed
             * for the new completer:
             */

            // alert("WOW");

            var content = msg.content;
            var start = content.cursor_start;
            var end = content.cursor_end;
            var matches = content.matches;
            var metadata = content.metadata || {};
            var extMetadata = metadata._jupyter_extended_metadata || {};

            console.log(content);

            var cur = this.editor.getCursor();

            var paragraph = content.paragraph;
            if (paragraph) {
                if (paragraph.cursor !== this.editor.indexFromPos(cur)
                    || paragraph.text !== this.editor.getValue()) {
                    this.close();
                    return;
                }
            }

            if (end === null) {
                // adapted message spec replies don't have cursor position info,
                // interpret end=null as current position,
                // and negative start relative to that
                end = this.editor.indexFromPos(cur);
                if (start === null) {
                    start = end;
                } else if (start < 0) {
                    start = end + start;
                }
            } else {
                // handle surrogate pairs
                var text = this.editor.getValue();
                end = utils.char_idx_to_js_idx(end, text);
                start = utils.char_idx_to_js_idx(start, text);
            }

            var results = CodeMirror.contextHint(this.editor);
            var filtered_results = [];
            //remove results from context completion
            //that are already in kernel completion
            var i;
            for (i=0; i < results.length; i++) {
                if (!_existing_completion(results[i].str, matches)) {
                    filtered_results.push(results[i]);
                }
            }

            // append the introspection result, in order, at at the beginning of
            // the table and compute the replacement range from current cursor
            // position and matched_text length.
            var from = this.editor.posFromIndex(start);
            var to = this.editor.posFromIndex(end);
            for (i = matches.length - 1; i >= 0; --i) {
                var info = extMetadata[i] || {};
                var replaceText = info.text || matches[i];
                var displayText = info.displayText || replaceText;

                filtered_results.unshift({
                    str: displayText,
                    replaceText: replaceText,
                    tail: info.tail,
                    icon: info.icon,
                    type: "introspection",
                    from: from,
                    to: to
                });
            }

            // one the 2 sources results have been merge, deal with it
            this.raw_result = filtered_results;

            // if empty result return
            if (!this.raw_result || !this.raw_result.length) return;

            // When there is only one completion, use it directly.
            if (this.do_auto_print && this.autopick && this.raw_result.length == 1) {
                this.insert(this.raw_result[0]);
                return;
            }

            if (this.raw_result.length == 1) {
                // test if first and only completion totally matches
                // what is typed, in this case dismiss
                var str = this.raw_result[0].str;
                var pre_cursor = this.editor.getRange({
                    line: cur.line,
                    ch: cur.ch - str.length
                }, cur);
                if (pre_cursor == str) {
                    this.close();
                    return;
                }
            }

            if (!this.visible) {
                this.complete = $('<div/>').addClass('completions');
                this.complete.attr('id', 'complete');

                // Currently webkit doesn't use the size attr correctly. See:
                // https://code.google.com/p/chromium/issues/detail?id=4579
                this.sel = $('<ul/>')
                    .attr('tabindex', -1)
                    .attr('multiple', 'true')
                    .addClass('jp-Completer');
                this.complete.append(this.sel);
                this.visible = true;
                $('body').append(this.complete);

                //build the container
                var that = this;
                this._handle_keydown = function (cm, event) {
                    that.keydown(event);
                };
                this.editor.on('keydown', this._handle_keydown);
                this._handle_keypress = function (cm, event) {
                    that.keypress(event);
                };
                this.editor.on('keypress', this._handle_keypress);
            }
            this.sel.attr('size', Math.min(10, this.raw_result.length));

            // After everything is on the page, compute the position.
            // We put it above the code if it is too close to the bottom of the page.
            var pos = this.editor.cursorCoords(
                this.editor.posFromIndex(start)
            );
            var left = pos.left-3;
            var top;
            var cheight = this.complete.height();
            var wheight = $(window).height();
            if (pos.bottom+cheight+5 > wheight) {
                top = pos.top-cheight-4;
            } else {
                top = pos.bottom+1;
            }
            this.complete.css('left', left + 'px');
            this.complete.css('top', top + 'px');

            // Clear and fill the list.
            this.sel.text('');
            this.build_gui_list(this.raw_result);
            return true;
        };

        Completer.prototype.pickColor = function(iconText) {
            this.colorsDict = this.colorsDict || {};
            var colorInd = this.colorsDict[iconText];
            if (colorInd) {
                return colorInd;
            }
            colorInd = Math.floor(Math.random() * 10) + 1;
            this.colorsDict[iconText] = colorInd;
            return colorInd;
        };

        Completer.prototype.insert = function (completion) {
            this.editor.replaceRange(completion.replaceText, completion.from, completion.to);
        };

        Completer.prototype.build_gui_list = function (completions) {
            var MAXIMUM_GUI_LIST_LENGTH = 1000;
            var that = this;
            for (var i = 0; i < completions.length && i < MAXIMUM_GUI_LIST_LENGTH; ++i) {
                var comp = completions[i];

                var icon = comp.icon || 'u';
                var text = comp.replaceText || '';
                var displayText = comp.str || '';
                var tail = comp.tail || '';

                var typeColorIndex = this.pickColor(icon);
                var iconTag = $('<span/>')
                    .text(icon.charAt(0))
                    .addClass('jp-Completer-type')
                    .attr('data-color-index', typeColorIndex);

                var matchTag = $('<span/>').text(displayText).addClass('jp-Completer-match');
                var typeTag = $('<span/>').text(tail).addClass('jp-Completer-typeExtended');

                var opt = $('<li/>').addClass('jp-Completer-item');
                opt.click((function (k, event) {
                    this.selIndex = k;
                    this.pick();
                    this.editor.focus();
                }).bind(that, i));

                opt.append(iconTag, matchTag, typeTag);

                this.sel.append(opt);
            }
            this.sel.children().first().addClass('jp-mod-active');
            this.selIndex = 0;
            // this.sel.scrollTop(0);
        };

        Completer.prototype.close = function () {
            this.done = true;
            $('#complete').remove();
            this.editor.off('keydown', this._handle_keydown);
            this.editor.off('keypress', this._handle_keypress);
            this.visible = false;
        };

        Completer.prototype.pick = function () {
            var ind = this.selIndex;
            var completion = this.raw_result[ind];
            this.insert(completion);
            this.close();
        };

        Completer.prototype.selectNew = function(newIndex) {
            $(this.sel.children()[this.selIndex]).removeClass('jp-mod-active');
            this.selIndex = newIndex;

            var active = this.sel.children()[this.selIndex];
            $(active).addClass('jp-mod-active');
            active.scrollIntoViewIfNeeded(false);
        };

        Completer.prototype.keydown = function (event) {
            var code = event.keyCode;

            // Enter
            var optionsLen;
            var index;
            var prevIndex;
            if (code == keycodes.enter) {
                event.codemirrorIgnore = true;
                event._ipkmIgnore = true;
                event.preventDefault();
                this.pick();
                // Escape or backspace
            } else if (code == keycodes.esc || code == keycodes.backspace) {
                event.codemirrorIgnore = true;
                event._ipkmIgnore = true;
                event.preventDefault();
                this.close();
            } else if (code == keycodes.tab) {
                //all the fastforwarding operation,
                //Check that shared start is not null which can append with prefixed completion
                // like %pylab , pylab have no shred start, and ff will result in py<tab><tab>
                // to erase py

                var sh = shared_start(this.raw_result, true);
                if (sh.str !== '') {
                    this.insert(sh);
                }
                this.close();
                this.carry_on_completion();

                // event.codemirrorIgnore = true;
                // event._ipkmIgnore = true;
                // event.preventDefault();
                // this.pick();
                // this.close();

            } else if (code == keycodes.up || code == keycodes.down) {
                // need to do that to be able to move the arrow
                // when on the first or last line ofo a code cell
                event.codemirrorIgnore = true;
                event._ipkmIgnore = true;
                event.preventDefault();

                optionsLen = this.raw_result.length;
                index = this.selIndex;
                if (code == keycodes.up) {
                    index--;
                }
                if (code == keycodes.down) {
                    index++;
                }
                index = Math.min(Math.max(index, 0), optionsLen-1);
                this.selectNew(index);
            } else if (code == keycodes.pageup || code == keycodes.pagedown) {
                event.codemirrorIgnore = true;
                event._ipkmIgnore = true;

                optionsLen = this.raw_result.length;
                index = this.selIndex;
                if (code == keycodes.pageup) {
                    index -= 10; // As 10 is the hard coded size of the drop down menu
                } else {
                    index += 10;
                }
                index = Math.min(Math.max(index, 0), optionsLen-1);
                this.selectNew(index);
            } else if (code == keycodes.left || code == keycodes.right) {
                this.close();
            }
        };

        Completer.prototype.keypress = function (event) {
            /**
             * FIXME: This is a band-aid.
             * on keypress, trigger insertion of a single character.
             * This simulates the old behavior of completion as you type,
             * before events were disconnected and CodeMirror stopped
             * receiving events while the completer is focused.
             */

            var that = this;
            var code = event.keyCode;

            // don't handle keypress if it's not a character (arrows on FF)
            // or ENTER/TAB
            if (event.charCode === 0 ||
                code == keycodes.tab ||
                code == keycodes.enter
            ) return;

            this.close();
            this.editor.focus();
            /*
            setTimeout(function () {
                that.carry_on_completion();
            }, 50);
             */
        };

        CodeCell.prototype._isCompletionEvent = function(event) {
            if (event.type !== 'keydown' || event.ctrlKey || event.metaKey || !this.tooltip._hidden)
                return false;
            if (event.keyCode === keycodes.tab)
                return true;

            var key = event.key;

            return /^[.]$/.test(key);
        };

        CodeCell.prototype.handle_codemirror_keyevent = function (editor, event) {

            var that = this;

            this.code_mirror.getAllMarks().filter(it => it.className === "cm__red_wavy_line").forEach(it => it.clear());

            // whatever key is pressed, first, cancel the tooltip request before
            // they are sent, and remove tooltip if any, except for tab again
            var tooltip_closed = null;
            if (event.type === 'keydown' && event.which !== keycodes.tab ) {
                tooltip_closed = this.tooltip.remove_and_cancel_tooltip();
            }

            var cur = editor.getCursor();
            if (event.keyCode === keycodes.enter){
                this.auto_highlight();
            }

            if (event.which === keycodes.down && event.type === 'keypress' && this.tooltip.time_before_tooltip >= 0) {
                // triger on keypress (!) otherwise inconsistent event.which depending on plateform
                // browser and keyboard layout !
                // Pressing '(' , request tooltip, don't forget to reappend it
                // The second argument says to hide the tooltip if the docstring
                // is actually empty
                this.tooltip.pending(that, true);
            } else if ( tooltip_closed && event.which === keycodes.esc && event.type === 'keydown') {
                // If tooltip is active, cancel it.  The call to
                // remove_and_cancel_tooltip above doesn't pass, force=true.
                // Because of this it won't actually close the tooltip
                // if it is in sticky mode. Thus, we have to check again if it is open
                // and close it with force=true.
                if (!this.tooltip._hidden) {
                    this.tooltip.remove_and_cancel_tooltip(true);
                }
                // If we closed the tooltip, don't let CM or the global handlers
                // handle this event.
                event.codemirrorIgnore = true;
                event._ipkmIgnore = true;
                event.preventDefault();
                return true;
            } else if (event.keyCode === keycodes.tab && event.type === 'keydown' && event.shiftKey) {
                if (editor.somethingSelected() || editor.getSelections().length !== 1){
                    var anchor = editor.getCursor("anchor");
                    var head = editor.getCursor("head");
                    if( anchor.line !== head.line){
                        return false;
                    }
                }
                var pre_cursor = editor.getRange({line:cur.line,ch:0},cur);
                if (pre_cursor.trim() === "") {
                    // Don't show tooltip if the part of the line before the cursor
                    // is empty.  In this case, let CodeMirror handle indentation.
                    return false;
                }
                this.tooltip.request(that);
                event.codemirrorIgnore = true;
                event.preventDefault();
                return true;
            } else if (this._isCompletionEvent(event)) {
                // Tab completion.
                this.tooltip.remove_and_cancel_tooltip();

                // completion does not work on multicursor, it might be possible though in some cases
                if (editor.somethingSelected() || editor.getSelections().length > 1) {
                    return false;
                }
                var pre_cursor = editor.getRange({line:cur.line,ch:0},cur);
                if (pre_cursor.trim() === "") {
                    // Don't autocomplete if the part of the line before the cursor
                    // is empty.  In this case, let CodeMirror handle indentation.
                    return false;
                } else {
                    event.preventDefault();
                    event.codemirrorIgnore = true;

                    var doAutoPrint = event.keyCode === keycodes.tab;

                    if (!doAutoPrint) {
                        editor.replaceRange(event.key, cur, cur);
                    }

                    this.completer.startCompletion(doAutoPrint);
                    return true;
                }
            }

            // keyboard event wasn't one of those unique to code cells, let's see
            // if it's one of the generic ones (i.e. check edit mode shortcuts)
            return Cell.prototype.handle_codemirror_keyevent.apply(this, [editor, event]);
        };

        CodeCell.prototype._handle_execute_reply = function (msg) {
            this.set_input_prompt(msg.content.execution_count);
            this.element.removeClass("running");
            this.events.trigger('set_dirty.Notebook', {value: true});

            if (msg.content.status === 'error') {
                var addInfo = msg.content.additionalInfo || {};
                var from = {line: addInfo.lineStart - 1, ch: addInfo.colStart - 1};
                var to;
                if (addInfo.lineEnd !== -1 && addInfo.colEnd !== -1) {
                    to = {line: addInfo.lineEnd - 1, ch: addInfo.colEnd - 1};
                } else {
                    to = {line: from.line, ch: from.ch + 3};
                }

                if (from.line !== undefined && from.ch !== undefined) {
                    this.code_mirror.markText(from, to, {className: "cm__red_wavy_line"});
                }
            }
        };

    }

    return {onload: onload};

});