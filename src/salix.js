/**
 * Copyright (c) Tijs van der Storm <Centrum Wiskunde & Informatica>.
 * All rights reserved.
 *
 * This file is licensed under the BSD 2-Clause License, which accompanies this project
 * and is available under https://opensource.org/licenses/BSD-2-Clause.
 * 
 * Contributors:
 *  - Tijs van der Storm - storm@cwi.nl - CWI
 */

function Salix(aRootId) {
	var rootId = aRootId || 'root';

	// 'native 'dom elements
	var builders = {};

	// currently active subscriptions
	var subscriptions = {};
	
	// signals whether a new rendering is requested
	// during that time, we won't process events
	var renderRequested = true;
	
	// queue of pending commands, events, subscription events
	var queue = [];
	
	function start() {
		$.get('/init', {}, step).always(doSome);
	}
	
	function root() {
		return document.getElementById(rootId);
	}

		
	// event is either an ordinary event or {message: ...} from sub.
	function handle(event) {
		// if doSome didn't do anything, we trigger the loop again here
		// because there's work now.
		if (queue.length == 0) {
			window.requestAnimationFrame(doSome);
		}
		queue.push(event);
	}
	
	
	function doSome() {
		if (!renderRequested) {
			while (queue.length > 0) {
				var event = queue.shift();
				if (isStale(event)) {
					console.log('Stale event');
					continue;
				}
				renderRequested = true;
				$.get('/msg', event.message, step).fail(function () {
					renderRequested = false;
					window.requestAnimationFrame(doSome);
				}); 
				break; // process one event at a time
			}
		}
	}
	
	function step(payload) {
		render(payload.patch);
		doCommands(payload.commands);
		subscribe(payload.subs);
		// I don't understand why, but putting these in 
		// .always on the get request doesn't work....
		renderRequested = false;
		window.requestAnimationFrame(doSome);
	}
	
	function render(patch) {
		patchDOM(root(), patch, replacer(root().parentNode, root()));	
	}
	
	function doCommands(cmds) {
		var prepend = [];
		for (var i = 0; i < cmds.length; i++) {
			var cmd = cmds[i];
			if (cmd.none) { // legacy; let's move to list[Cmd] again...
				continue;
			}
			var data = Commands[cmd.command.name](cmd.command.args);

			prepend.push({message: makeMessage(cmd.command.handle.handle, data)});
		}
		for (var i = prepend.length - 1; i >= 0; i--) {
			// unshift in reverse, so that first executed command
			// is handled first.
			queue.unshift(prepend[i]);
		}
	}
	
	function subscribe(subs) {
		for (var i = 0; i < subs.length; i++) {
			var sub = subs[i];
			var id = sub.subscription.handle.handle.id;
			if (subscriptions.hasOwnProperty(id)) {
				continue;
			}
			subscriptions[id] = Subscriptions[sub.subscription.name](sub.subscription.handle.handle, 
									sub.subscription.args);
		}
		unsubscribeStaleSubs(subs);
	}

	function unsubscribeStaleSubs(subs) {
		// TODO: fix this abomination
		var toDelete = [];
		
		outer: for (var k in subscriptions) {
			if (subscriptions.hasOwnProperty(k)) {
				for (var i = 0; i < subs.length; i++) {
					var sub = subs[i];
					var id = sub.subscription.handle.handle.id;
					if (('' + id) === k) {
						continue outer;
					}
				}
				toDelete.push(k);
			}
		}
		for (var i = 0; i < toDelete.length; i++) {
			subscriptions[toDelete[i]](); // shutdown
			delete subscriptions[toDelete[i]];
		}
	}

	function isStale(event) {
		if (!event.target) {
			return false; // subscription, command, or 'native'
		}
		if (event.handler.stale) {
			return true;
		}
		return isStaleDOM(event.target);
	}
	
	function isStaleDOM(dom) {
		if (dom === null) {
			return true;
		}
		if (dom === document) {
			return false;
		}
		return isStaleDOM(dom.parentNode);
	}
	
	function makeMessage(handle, data) {
		if (!data) {
			return; // TODO: don't encode "not handling the event" by undefined data.
		}
		var result = {id: handle.id};
		if (handle.maps) {
			result.maps = handle.maps.join(';'); 
		}
		for (var k in data) {
			if (data.hasOwnProperty(k)) {
				result[k] = data[k];
			}
		}
		return result;
	}

	function nodeType(node) {
		for (var type in node) { break; }
		return type;
	}


	function patchThis(dom, edits, attach) {
		edits = edits || [];

		for (var i = 0; i < edits.length; i++) {
			var edit = edits[i];
			var type = nodeType(edit);

			switch (type) {
			
			case 'replace':
				build(edit[type].html, attach);

			case 'setText': 
				dom.nodeValue = edit[type].contents;
				break;			
				
			case 'removeNode': 
				dom.removeChild(dom.lastChild);
				break;
				
			case 'appendNode':
				build(edit[type].html, appender(dom));
				break;
				
			case 'setAttr': 
				dom.setAttribute(edit[type].name, edit[type].val);
				break;
				
			case 'setProp': 
				dom[edit[type].name] = edit[type].val;
				break;
				
			case 'setEvent':
				var key = edit[type].name;
				var h = edit[type].handler;
				var handler = getHandler(h);
				setEventListener(dom, key, handler);
				break
			
			case 'removeAttr': 
				dom.removeAttribute(edit[type].name);
				break;
				
			case 'removeProp': 
				delete dom[edit[type].name];
				break;
				
			case 'removeEvent': 
				var key = edit[type].name;
				var handler = dom.salix_handlers[key];
				handler.stale = true;
				dom.removeEventListener(key, handler);
				delete dom.salix_handlers[key]
				break;
				
			default: 
				throw 'unsupported edit: ' + JSON.stringify(edit);
				
			}
		}
	}
	
	function replacer(dom, oldKid) {
		return function (newKid) { dom.replaceChild(newKid, oldKid); };
	}
	
	function appender(dom) {
		return function (kid) { dom.appendChild(kid); };
	}
	
	function patchDOM(dom, tree, attach) {
		if (dom.salix_native) {
			dom.salix_native.patch(tree.patch.edits, attach)
		} 
		else {
			patchThis(dom, tree.patch.edits, attach);
		}
		
		// NB: (native || replace in edits) implies tree.patch.patches == []
		var patches = tree.patch.patches || [];
		for (var i = 0; i < patches.length; i++) {
			var p = patches[i];
			var kid = dom.childNodes[p.patch.pos];
			patchDOM(kid, p, replacer(dom, kid));
		}
		
	}

	function setEventListener(dom, key, handler) {
		var allHandlers = dom.salix_handlers || {};
		if (allHandlers.hasOwnProperty(key)) {
			dom.removeEventListener(key, allHandlers[key]);
			allHandlers[key].stale = true;
		}
		allHandlers[key] = handler;
		dom.addEventListener(key, handler);
		dom.salix_handlers = allHandlers;
		return handler;
	}

	
	
	function build(vdom, attach) {
	    if (vdom.txt) {
	        attach(document.createTextNode(vdom.txt.contents));
	        return;
	    }

	    var type = nodeType(vdom);
	    var vattrs = vdom[type].attrs || {};
	    var vprops = vdom[type].props || {};
	    var vevents = vdom[type].events || {};

	    if (vdom.native) {
	    	var native = vdom.native;
	    	builders[native.kind](attach, native.id, vattrs, vprops, vevents, native.extra);
	    	return;
	    }

	    // an element
	    
	    var elt = vprops.namespace != undefined
	            ? document.createElementNS(vprops.namespace, vdom.element.tagName)
	            : document.createElement(vdom.element.tagName);
	    
	    updateAttrsPropsAndEvents(elt, vattrs, vprops, vevents);       
	    
	    attach(elt);
	    for (var i = 0; i < vdom.element.kids.length; i++) {
	    	build(vdom.element.kids[i], appender(elt));
	    }
	    
	}
	
	function updateAttrsPropsAndEvents(elt, vattrs, vprops, vevents) {
		for (var k in vattrs) {
	        if (vattrs.hasOwnProperty(k)) {
	            elt.setAttribute(k, vattrs[k]);
	        }
	    }
	    
	    for (var k in vprops) {
	    	if (vprops.hasOwnProperty(k)) {
	    		elt[k] = vprops[k];
	    	}
	    }
	    
	    for (var k in vevents) {
	    	if (vevents.hasOwnProperty(k)) {
	    		setEventListener(elt, k, getHandler(vevents[k]));
	    	}
	    }
	}

	// Basic library of commands and subscriptions
	// can be extended by 'natives'.
	// TODO: this seems way too complex...
	
	var Subscriptions = {
			timeEvery: function (h, args) {
				var timer = setInterval(function() {
					var data = {type: 'integer', value: (new Date().getTime() / 1000) | 0};
					handle({message: makeMessage(h, data)}); 
				}, args.interval);
				return function () { clearInterval(timer); };
			}
	};
	
	var Commands = {
			random: function (args) {
				var to = args.to;
				var from = args.from;
				var random = Math.floor(Math.random() * (to - from + 1)) + from;
				return {type: 'integer', value: random};
			}
	};
	
	
	function getDecoder(hnd) {
		return Decoders[hnd.handler.name](hnd.handler.args);
	}
	
	function getHandler(hnd) {
		var handler = function (event) {
			event.message = makeMessage(hnd.handler.handle.handle, getDecoder(hnd)(event));
			if (event.message) {
				event.handler = handler; // used to detect staleness
				handle(event);
			}
		}
		return handler;
	}
	
	function getNativeHandler(hnd) {
		return function (arg0, arg1, arg2, arg3) {
			var event = {}; // simulate ordinary event
			event.message = makeMessage(hnd.handler.handle.handle, getDecoder(hnd)(arg0, arg1, arg2, arg3))
			if (event.message) {
				handle(event);
			}
		};
	}
	
	var Decoders = {
			succeed: function (args) {
				return function (e) { return {type: 'nothing'}; };
			},
			targetValue: function (args) {
				return function (e) { return {type: 'string', value: e.target.value}; };
			},
			targetChecked: function (args) {
				return function (e) { return {type: 'boolean', value: e.target.checked}; };
			},
			theKeyCode: function (args) {
				return function (e) {
					if (e.keyCode === args.keyCode) {
						return {type: 'nothing'};
					}
				};
			}
	};
	
	
	function registerNative(kind, builder) {
		builders[kind] = builder;
	}
	
	return {start: start, 
			registerNative: registerNative,
			build: build,
			nodeType: nodeType,
			getNativeHandler: getNativeHandler,
			Subscriptions: Subscriptions,
			Decoders: Decoders,
			Commands: Commands};
}



