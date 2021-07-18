/*
    This file includes code of Jupyter notebook project (https://github.com/jupyter/notebook)
    which is licensed under the terms of the Modified BSD License
    (also known as New or Revised or 3-Clause BSD), as follows:

    - Copyright (c) 2001-2015, IPython Development Team
    - Copyright (c) 2015-, Jupyter Development Team

    All rights reserved.

    Full license text is available in additional-licenses/LICENSE_BSD_3 file
*/

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
        var Kernel = requirejs("services/kernels/kernel").Kernel;

        var error_class = "cm__red_wavy_line";
        var warning_class = "cm__orange_wavy_line";
        var additionalClasses = [error_class, warning_class];
        var diag_class = {
            "ERROR": error_class,
            "WARNING": warning_class
        };

        function rectIntersect(a, b) {
            return (a.left <= b.right &&
                b.left <= a.right &&
                a.top <= b.bottom &&
                b.top <= a.bottom)
        }

        function isOnScreen(elem) {
            var bounding = elem.getBoundingClientRect();
            return rectIntersect(
                bounding,
                {
                    top: 0,
                    left: 0,
                    bottom: (window.innerHeight || document.documentElement.clientHeight),
                    right: (window.innerWidth || document.documentElement.clientWidth)
                }
            )
        }

        var opened_completer = null;
        var siteEl = document.getElementById("site");
        $(siteEl).scroll((e) => {
            if (opened_completer && !isOnScreen(opened_completer.complete[0])) {
                opened_completer.close();
            }
        });

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
                var str = B[i].replaceText;
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

        function indexOf(str, filter, from, to, step) {
            var cond = (from < to) ? (j => j <= to) : (j => j >= to);
            for (var i = from; cond(i); i += step) {
                if (filter(str[i]))
                    return i;
            }
            return -1;
        }

        function getTokenBounds(buf, cursor, editor) {
            if (cursor > buf.length) {
                throw new Error("Position " + cursor + " does not exist in code snippet <" + buf + ">");
            }

            var filter = c => !/^[A-Z0-9_]$/i.test(c);

            var start = indexOf(buf, filter, cursor - 1, 0, -1) + 1;
            var end = cursor

            return {
                before: buf.substring(0, start),
                token: buf.substring(start, end),
                tokenBeforeCursor: buf.substring(start, cursor),
                after: buf.substring(end, buf.length),
                start: start,
                end: end,
                posStart: editor.posFromIndex(start),
                posEnd: editor.posFromIndex(end)
            }
        }

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

            this.autopick = !!first_invocation;

            // We want a single cursor position.
            if (this.editor.somethingSelected()|| this.editor.getSelections().length > 1) {
                return;
            }

            var cursor_pos = this.editor.indexFromPos(cur);
            var text = this.editor.getValue();
            cursor_pos = utils.js_idx_to_char_idx(cursor_pos, text);

            var prevBounds = this.tokenBounds;
            var bounds = getTokenBounds(text, cursor_pos, this.editor);
            this.tokenBounds = bounds;
            if (prevBounds && this.raw_result) {
                if (bounds.before === prevBounds.before &&
                    bounds.after === prevBounds.after &&
                    bounds.end > prevBounds.end) {

                    var newResult = this.raw_result.filter((v) => {
                        var displayName = v.str;
                        if (displayName[0] === '`')
                            displayName = displayName.substring(1, displayName.length - 1);
                        return displayName.startsWith(bounds.tokenBeforeCursor)
                    }).map((completion) => {
                        completion.from = bounds.posStart;
                        completion.to = bounds.posEnd;
                        return completion;
                    });

                    if (newResult.length > 0) {
                        this.raw_result = newResult;
                        this.make_gui(this.prepare_cursor_pos(bounds.start, cursor_pos));
                        return;
                    }
                }
            }

            // one kernel completion came back, finish_completing will be called with the results
            // we fork here and directly call finish completing if kernel is busy

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

        function convertPos(pos) {
            return {
                line: pos.line - 1,
                ch: pos.col - 1
            }
        }

        function adjustRange(start, end) {
            var s = convertPos(start);
            var e = convertPos(end);
            if (s.line === e.line && s.ch === e.ch) {
                s.ch --;
            }
            return {
                start: s,
                end: e
            }
        }

        function highlightErrors (errors, cell) {
            errors = errors || [];
            errors.forEach(error => {
                var start = error.start;
                var end = error.end;
                if (!start || !end)
                    return;
                var r = adjustRange(start, end);
                error.range = r;
                var cl = diag_class[error.severity];
                cell.code_mirror.markText(r.start, r.end, {className: cl});
            });

            cell.errorsList = errors;
        }

        Cell.prototype.highlightErrors = function (errors) {
            highlightErrors(errors, this)
        };

        Completer.prototype.make_gui = function(cur_pos, cur) {
            var start = cur_pos.start;
            cur = cur || this.editor.getCursor();

            // if empty result return
            if (!this.raw_result || !this.raw_result.length) {
                this.close();
                return;
            }

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
                $(siteEl).append(this.complete);

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

            // Clear and fill the list.
            this.sel.text('');
            this.build_gui_list(this.raw_result);

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

            this.complete.css('left', left + siteEl.scrollLeft - siteEl.offsetLeft + 'px');
            this.complete.css('top', top + siteEl.scrollTop - siteEl.offsetTop + 'px');

            opened_completer = this;
            return true;
        };

        Completer.prototype.prepare_cursor_pos = function(start, end) {
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

            return {
                start: start,
                end: end
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

            // If completion error occurs
            if (matches === undefined) {
                return;
            }

            var cur = this.editor.getCursor();

            var paragraph = content.paragraph;
            if (paragraph) {
                if (paragraph.cursor !== this.editor.indexFromPos(cur)
                    || paragraph.text !== this.editor.getValue()) {
                    // this.close();
                    return;
                }
            }

            var newPos = this.prepare_cursor_pos(start, end);
            start = newPos.start;
            end = newPos.end;

            var filtered_results = [];
            //remove results from context completion
            //that are already in kernel completion
            var i;

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
                    deprecation: info.deprecation,
                    from: from,
                    to: to
                });
            }

            // one the 2 sources results have been merge, deal with it
            this.raw_result = filtered_results;

            this.make_gui(newPos, cur);
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
                if (comp.deprecation != null) {
                    matchTag.addClass('jp-Completer-deprecated');
                }

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
            opened_completer = null;
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
            if (code == keycodes.enter && !(event.shiftKey || event.ctrlKey || event.metaKey || event.altKey)) {
                event.codemirrorIgnore = true;
                event._ipkmIgnore = true;
                event.preventDefault();
                this.pick();
                // Escape or backspace
            } else if (code == keycodes.esc) {
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

        function _isCompletionKey(key) {
            return /^[A-Z0-9.:"]$/i.test(key);
        }

        function _processCompletionOnChange(cm, changes, completer) {
            var close_completion = false
            for (var i = 0; i < changes.length; ++i) {
                var change = changes[i];
                if (change.origin === "+input") {
                    for (var j = 0; j < change.text.length; ++j) {
                        var t = change.text[j];
                        for (var k = 0; k < t.length; ++k) {
                            if (_isCompletionKey(t[k])) {
                                completer.startCompletion(false);
                                return;
                            }
                        }
                    }
                } else {
                    var line = change.from.line;
                    var ch = change.from.ch;
                    if (ch === 0) continue;
                    var removed = change.removed;
                    if (removed.length > 1 || removed[0].length > 0) {
                        var prevChar = cm.getRange({line: line, ch: ch - 1}, change.from);
                        if (_isCompletionKey(prevChar)) {
                            completer.startCompletion(false);
                            return;
                        }
                        else close_completion = true;
                    }
                }
            }
            if (close_completion) completer.close();
        }

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
            // or ENTER/TAB/BACKSPACE
            if (event.charCode === 0 ||
                code == keycodes.tab ||
                code == keycodes.enter ||
                code == keycodes.backspace
            ) return;

            if (_isCompletionKey(event.key))
                return;

            // this.close();
            this.editor.focus();

            setTimeout(function () {
                that.carry_on_completion();
            }, 10);
        };

        var EMPTY_ERRORS_RESULT = [[], []];

        CodeCell.prototype.findErrorsAtPos = function(pos) {
            if (pos.outside || Math.abs(pos.xRel) > 10)
                return EMPTY_ERRORS_RESULT;

            var ind = this.code_mirror.indexFromPos(pos);
            if (!this.errorsList)
                return EMPTY_ERRORS_RESULT;

            var filter = (er) => {
                var er_start_ind = this.code_mirror.indexFromPos(er.range.start);
                var er_end_ind = this.code_mirror.indexFromPos(er.range.end);
                return er_start_ind <= ind && ind <= er_end_ind;
            };
            var passed = [];
            var other = [];
            this.errorsList.forEach((er) => {
                if (filter(er)) {
                    passed.push(er);
                } else {
                    other.push(er);
                }
            });
            return [passed, other];
        };

        function clearErrors(cell) {
            cell.code_mirror.getAllMarks()
                .filter(it => additionalClasses.some((cl) => it.className === cl))
                .forEach(it => it.clear());
            cell.errorsList = []
        }

        function clearAllErrors(notebook) {
            notebook.get_cells().forEach((cell) =>{
                if (cell.code_mirror) {
                    clearErrors(cell);
                }
            });
        }

        CodeCell.prototype._handle_change = function(cm, changes) {
            _processCompletionOnChange(cm, changes, this.completer)

            clearAllErrors(this.notebook);
            this.kernel.listErrors(cm.getValue(), (msg) => {
                var content = msg.content;
                console.log(content);

                if(content.code !== cm.getValue()) {
                    return;
                }

                var errors = content.errors;
                this.highlightErrors(errors);
                this.errorsList = errors;

                cm.scrollIntoView(null)
            });
        };

        CodeCell.prototype._handle_move = function(e) {
            var rect = e.currentTarget.getBoundingClientRect();
            var x = e.clientX - rect.left;
            var y = e.clientY - rect.top;
            var cursor_pos = this.code_mirror.coordsChar({left: x, top: y}, "local");
            var res = this.findErrorsAtPos(cursor_pos);
            var errors = res[0];
            var otherErrors = res[1];
            errors.forEach((error) => {
                tempTooltip(this.code_mirror, error, cursor_pos);
            });
            otherErrors.forEach((error) => {
                if (error.tip) {
                    remove(error.tip);
                }
            })
        };


        CodeCell.prototype._isCompletionEvent = function(event, cur, editor) {
            if (event.type !== 'keydown' || event.ctrlKey || event.metaKey || event.altKey || !this.tooltip._hidden)
                return false;
            if (event.keyCode === keycodes.tab)
                return true;
            if (event.keyCode === keycodes.backspace){
                var pre_cursor = editor.getRange({line:0,ch:0},cur);
                return pre_cursor.length > 1 && _isCompletionKey(pre_cursor[pre_cursor.length - 2]);
            }
            return _isCompletionKey(event.key);
        };

        CodeCell.prototype.addEvent = function(obj, event, listener, bind_listener_name) {
            if (this[bind_listener_name]) {
                return;
            }
            var handler = this[bind_listener_name] = listener.bind(this);
            CodeMirror.off(obj, event, handler);
            CodeMirror.on(obj, event, handler);
        };

        CodeCell.prototype.bindEvents = function() {
            this.addEvent(this.code_mirror, 'changes', this._handle_change, "binded_handle_change");
            this.addEvent(this.code_mirror.display.lineSpace, 'mousemove', this._handle_move, 'binded_handle_move');
        };

        CodeCell.prototype.handle_codemirror_keyevent = function (editor, event) {
            var that = this;
            this.bindEvents();

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
            } else if (this._isCompletionEvent(event, cur, editor)) {
                // Tab completion.
                this.tooltip.remove_and_cancel_tooltip();

                // completion does not work on multicursor, it might be possible though in some cases
                if (editor.somethingSelected() || editor.getSelections().length > 1) {
                    return false;
                }
                var pre_cursor = editor.getRange({line:cur.line,ch:0},cur);
                if (pre_cursor.trim() === "" && event.keyCode === keycodes.tab) {
                    // Don't autocomplete if the part of the line before the cursor
                    // is empty.  In this case, let CodeMirror handle indentation.
                    return false;
                } else {
                    if (event.keyCode === keycodes.tab) {
                        event.preventDefault();
                        event.codemirrorIgnore = true;
                        this.completer.startCompletion(true);
                    }
                    return true;
                }
            }

            // keyboard event wasn't one of those unique to code cells, let's see
            // if it's one of the generic ones (i.e. check edit mode shortcuts)
            return Cell.prototype.handle_codemirror_keyevent.apply(this, [editor, event]);
        };

        CodeCell.prototype._handle_execute_reply = function (msg) {
            clearAllErrors(this.notebook)
            this.bindEvents();

            this.set_input_prompt(msg.content.execution_count);
            this.element.removeClass("running");
            this.events.trigger('set_dirty.Notebook', {value: true});

            if (msg.content.status === 'error') {
                var addInfo = msg.content.additionalInfo || {};
                var from = {line: addInfo.lineStart, col: addInfo.colStart};
                var to;
                if (addInfo.lineEnd !== -1 && addInfo.colEnd !== -1) {
                    to = {line: addInfo.lineEnd, col: addInfo.colEnd};
                } else {
                    to = {line: from.line, col: from.col + 3};
                }

                if (from.line !== undefined && from.col !== undefined) {
                    var message = addInfo.message;
                    message = message.replace(/^\(\d+:\d+ - \d+\) /, "");
                    message = message.replace(/^\(\d+:\d+\) - \(\d+:\d+\) /, "");

                    this.errorsList = [
                        {
                            start: from,
                            end: to,
                            message: message,
                            severity: "ERROR"
                        }];
                    this.highlightErrors(this.errorsList);
                }
            }
        };

        function tempTooltip(cm, error, pos) {
            if (cm.state.errorTip) remove(cm.state.errorTip);
            var where = cm.charCoords(pos);
            var tip = makeTooltip(where.right + 1, where.bottom, error.message, error.tip);
            error.tip = cm.state.errorTip = tip;
            fadeIn(tip);

            function maybeClear() {
                old = true;
                if (!mouseOnTip) clear();
            }
            function clear() {
                cm.state.ternTooltip = null;
                if (tip.parentNode) fadeOut(tip);
                clearActivity();
            }
            var mouseOnTip = false, old = false;
            CodeMirror.on(tip, "mousemove", function() { mouseOnTip = true; });
            CodeMirror.on(tip, "mouseout", function(e) {
                var related = e.relatedTarget || e.toElement;
                if (!related || !CodeMirror.contains(tip, related)) {
                    if (old) clear();
                    else mouseOnTip = false;
                }
            });
            setTimeout(maybeClear, 100000);
            var clearActivity = onEditorActivity(cm, clear)
        }

        function fadeIn(tooltip) {
            document.body.appendChild(tooltip);
            tooltip.style.opacity = "1";
        }

        function fadeOut(tooltip) {
            tooltip.style.opacity = "0";
            setTimeout(function() { remove(tooltip); }, 100);
        }

        function makeTooltip(x, y, content, element) {
            var node = element || elt("div", "kotlin-error-tooltip", content);
            node.style.left = x + "px";
            node.style.top = y + "px";
            return node;
        }

        function elt(tagname, cls /*, ... elts*/) {
            var e = document.createElement(tagname);
            if (cls) e.className = cls;
            for (var i = 2; i < arguments.length; ++i) {
                var elt = arguments[i];
                if (typeof elt == "string") elt = document.createTextNode(elt);
                e.appendChild(elt);
            }
            return e;
        }

        function remove(node) {
            var p = node && node.parentNode;
            if (p) p.removeChild(node);
        }

        function onEditorActivity(cm, f) {
            cm.on("cursorActivity", f);
            cm.on("blur", f);
            cm.on("scroll", f);
            cm.on("setDoc", f);
            return function() {
                cm.off("cursorActivity", f);
                cm.off("blur", f);
                cm.off("scroll", f);
                cm.off("setDoc", f);
            }
        }

        Kernel.prototype.listErrors = function (code, callback) {
            var callbacks;
            if (callback) {
                callbacks = { shell : { reply : callback } };
            }
            var content = {
                code : code
            };
            return this.send_shell_message("list_errors_request", content, callbacks);
        };

    }

    return {onload: onload};

});