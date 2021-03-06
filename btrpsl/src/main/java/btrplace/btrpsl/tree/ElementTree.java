/*
 * Copyright (c) 2013 University of Nice Sophia-Antipolis
 *
 * This file is part of btrplace.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package btrplace.btrpsl.tree;

import btrplace.btrpsl.ANTLRBtrplaceSL2Parser;
import btrplace.btrpsl.ErrorReporter;
import btrplace.btrpsl.Script;
import btrplace.btrpsl.element.BtrpElement;
import btrplace.btrpsl.element.BtrpOperand;
import btrplace.model.Element;
import btrplace.model.view.NamingService;
import org.antlr.runtime.Token;

/**
 * A Tree parser to identify a virtual machine or a node.
 *
 * @author Fabien Hermenier
 */
public class ElementTree extends BtrPlaceTree {

    private Script script;

    private NamingService namingServiceNodes;
    private NamingService namingServiceVMs;

    /**
     * Make a new parser.
     *
     * @param t    the token to analyze
     * @param errs the errors to report
     */
    public ElementTree(Token t, NamingService nsNodes, NamingService nsVMs, Script scr, ErrorReporter errs) {
        super(t, errs);
        this.script = scr;
        this.namingServiceNodes = nsNodes;
        this.namingServiceVMs = nsVMs;
    }

    @Override
    public BtrpOperand go(BtrPlaceTree parent) {
        String lbl = getText();
        Element el;
        BtrpElement btrpEl;
        switch (token.getType()) {
            case ANTLRBtrplaceSL2Parser.NODE_NAME:
                String ref = lbl.substring(1, lbl.length());
                el = namingServiceNodes.resolve(lbl);
                if (el == null) {
                    return ignoreError("Unknown node '" + ref + "'");
                }
                btrpEl = new BtrpElement(BtrpOperand.Type.node, lbl , el);
                break;
            case ANTLRBtrplaceSL2Parser.IDENTIFIER:
                /**
                 * Switch to Fully Qualified name before getting the VM
                 */
                String fqn = script.id() + '.' + lbl;
                el = namingServiceVMs.resolve(fqn);
                if (el == null) {
                    return ignoreError("Unknown VM '" + lbl + "'");
                }
                btrpEl = new BtrpElement(BtrpOperand.Type.VM, fqn , el);
                break;
            default:
                return ignoreError("Unexpected type: " + ANTLRBtrplaceSL2Parser.tokenNames[token.getType()]);
        }
        return btrpEl;
    }
}
