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

function registerDagre(salix) {
	
	function dagreGraph(nodes, edges, props) {
		// props are interpreted on the Dagre graph
		var g = new dagreD3.graphlib.Graph().setGraph(props);
		
		function labelBuilder(label) {
			return function() {
				var myDomNode = undefined;
				salix.build(label, function(kid) {
					myDomNode = kid;
				});
				return myDomNode;
			};
		}
		
		for (var i = 0; i < nodes.length; i++) {
			var theNode = nodes[i].gnode;
			var nodeAttrs = {};
			nodeAttrs.label = labelBuilder(theNode.label);
			
			for (var k in theNode.attrs) {
				if (theNode.attrs.hasOwnProperty(k)) {
					nodeAttrs[k] = theNode.attrs[k];
				}
			}
			g.setNode(theNode.id, nodeAttrs);
		}
		
		for (var i = 0; i < edges.length; i++) {
			var theEdge = edges[i].gedge;
			g.setEdge(theEdge.from, theEdge.to, theEdge.attrs || {});
		}

		return g;
	}
	
	function myDagre(attach, id, attrs, props, events, extra) {

		
		//NB: used down below in patch
		var nodes = extra.nodes;
		var edges = extra.edges;
		
		var g = dagreGraph(nodes, edges, props);
		
		var _svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
		_svg.id = id;
		attach(_svg);
		
		// attrs are interpreted on the svg dom
		for (var k in attrs) {
			if (attrs.hasOwnProperty(k)) {
				_svg.setAttribute(k, attrs[k]);
			}
		}
		
		var svg = d3.select('#' + id),
	    	svgGroup = svg.append('g');
		
		var zoom = d3.behavior.zoom().on("zoom", function() {
		      svgGroup.attr("transform", "translate(" + d3.event.translate + ")" +
		                                     "scale(" + d3.event.scale + ")");
		    });
		svg.call(zoom);

		var render = new dagreD3.render();
		render(svgGroup, g);
		
		var initialScale = 1;
		zoom
		  .translate([(svg.attr("width") - g.graph().width * initialScale) / 2, 20])
		  .scale(initialScale)
		  .event(svg);
		svg.attr('height', g.graph().height * initialScale + 40);
		svg.attr('width', g.graph().width * initialScale + 40);
		
		function patch(edits, attach) {
			edits = edits || [];
			var newNodes;
			var newEdges;
			var rerender = false;
			
			for (var i = 0; i < edits.length; i++) {
				var edit = edits[i];
				var type = salix.nodeType(edit);

				switch (type) {
				
				case 'setAttr': 
					_svg.setAttribute(edit[type].name, edit[type].val);
					break;
					
				case 'removeAttr': 
					_svg.removeAttribute(edit[type].name);
					break;

				case 'setProp': 
					props[edit[type].name] = edit[type].val;
					rerender = true;
					break;
					
				case 'removeProp': 
					delete props[edit[type].name];
					rerender = true;
					break;

				case 'setExtra':
					if (edit.setExtra.name === 'nodes') {
						newNodes = edit.setExtra.value;
					}
					if (edit.setExtra.name === 'edges') {
						newEdges = edit.setExtra.value;
					}
					break;
				
				case 'replace':
					salix.build(edit[type].html, attach);
					break;
				}
			}
			
			if (newNodes && newEdges) {
				var newG = dagreGraph(newNodes, newEdges, props);
				nodes = newNodes;
				edges = newEdges;
				render(svgGroup, newG);
			}
			else if (newNodes) {
				var newG = dagreGraph(newNodes, edges, props);
				nodes = newNodes;
				render(svgGroup, newG);
			}
			else if (newEdges) {
				var newG = dagreGraph(nodes, newEdges, props);
				edges = newEdges;
				render(svgGroup, newG);
			}
			else if (rerender) { // because of props change
				var newG = dagreGraph(nodes, edges, props);
				render(svgGroup, newG);
			}
			
		}
		
        
		_svg.salix_native = {patch: patch};
		return _svg;
	}
	
	salix.registerNative('dagre', myDagre);
};