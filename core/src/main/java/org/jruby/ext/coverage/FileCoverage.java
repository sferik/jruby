/***** BEGIN LICENSE BLOCK *****
 * Version: EPL 2.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/

package org.jruby.ext.coverage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jruby.util.collections.IntList;

/**
 * Everything Coverage has collected for one source file: the value side of the hash returned by
 * <code>Coverage.result</code>. An instance is created when a file is parsed while coverage is set up.
 *
 * <ul>
 * <li>{@link #getLines()}: execution count per line (-1 for lines that hold no code), present only while
 * lines are being measured;</li>
 * <li>{@link #getMethods()}: one {@link MethodCoverage} for every method entry defined from this file since
 * coverage was set up, in definition order;</li>
 * <li>{@link #getBranches()}: one {@link BranchCoverage} for every branching construct of the file, in the
 * order the IR builder met them (which is the order MRI's compiler meets them and numbers them in).</li>
 * </ul>
 *
 * <p>The method entries are only ever touched while holding the {@link CoverageData} lock (registration,
 * clearing and result conversion all synchronize on it), so that list needs no synchronization of its own. The
 * branches are declared by the IR builder and their targets looked up by running code without that lock, so
 * declaration synchronizes on this instance and the lists are safe to read concurrently.</p>
 */
public final class FileCoverage {
    private IntList lines;
    private final List<MethodCoverage> methods = new ArrayList<>();
    private final List<BranchCoverage> branches = new CopyOnWriteArrayList<>();
    private final Map<String, BranchCoverage> branchesByKey = new HashMap<>();
    private final List<BranchTarget> branchTargets = new CopyOnWriteArrayList<>();

    public IntList getLines() {
        return lines;
    }

    void setLines(IntList lines) {
        this.lines = lines;
    }

    public List<MethodCoverage> getMethods() {
        return methods;
    }

    /**
     * Declare (or find) the branching construct of the given type at the given source span.
     *
     * @param type if, unless, case, while, until or &amp;.
     * @param startLine one-based line where the construct starts
     * @param startColumn zero-based byte column where it starts
     * @param endLine one-based line where it ends
     * @param endColumn zero-based byte column just past its end
     */
    public synchronized BranchCoverage declareBranch(String type, int startLine, int startColumn, int endLine, int endColumn) {
        String key = type + ':' + startLine + ':' + startColumn + ':' + endLine + ':' + endColumn;
        BranchCoverage branch = branchesByKey.get(key);

        if (branch == null) {
            branch = new BranchCoverage(this, type, startLine, startColumn, endLine, endColumn);
            branchesByKey.put(key, branch);
            branches.add(branch);
        }

        return branch;
    }

    /**
     * The branching constructs in declaration order.
     */
    public List<BranchCoverage> getBranches() {
        return branches;
    }

    /**
     * The branch target with the given {@link BranchTarget#getIndex() index}, or null.
     */
    public BranchTarget getBranchTarget(int index) {
        return index >= 0 && index < branchTargets.size() ? branchTargets.get(index) : null;
    }

    synchronized int registerBranchTarget() {
        return branchTargets.size();
    }

    synchronized void addBranchTarget(BranchTarget target) {
        branchTargets.add(target);
    }
}
