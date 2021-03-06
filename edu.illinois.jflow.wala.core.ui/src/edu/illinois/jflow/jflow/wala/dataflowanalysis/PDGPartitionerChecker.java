package edu.illinois.jflow.jflow.wala.dataflowanalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.cast.java.ipa.modref.AstJavaModRef;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.OrdinalSet;

/**
 * Acts as a facade to check the validity of the selected statements. Checks in sequence:
 * <ol>
 * <li>Constructs the different stages, including the generator</li>
 * <li>Checks for loop-carried dependencies of scalar variables</li>
 * <li>Checks for interference of heap variables</li>
 * </ol>
 * 
 * @author nchen
 * 
 */
public class PDGPartitionerChecker {
	private ProgramDependenceGraph pdg; // Need a reference to the program dependence graph

	private List<PipelineStage> stages= new ArrayList<PipelineStage>();

	private Map<PDGNode, Integer> node2stage= new HashMap<PDGNode, Integer>();

	private AstJavaModRef modref;

	private Map<CGNode, OrdinalSet<PointerKey>> mod;

	private Map<CGNode, OrdinalSet<PointerKey>> ref;

	private CallGraph callGraph;

	public static PDGPartitionerChecker makePartitionChecker(ProgramDependenceGraph pdg, List<List<Integer>> selections) {
		PDGPartitionerChecker temp= new PDGPartitionerChecker(pdg);
		temp.convertSelectionToStages(selections);
		temp.mapNodesToStages();
		return temp;
	}

	private PDGPartitionerChecker(ProgramDependenceGraph pdg) {
		this.pdg= pdg;
	}

	public List<PipelineStage> convertSelectionToStages(List<List<Integer>> selections) {
		for (List<Integer> selection : selections) {
			PipelineStage stage= PipelineStage.makePipelineStage(pdg, selection);
			stages.add(stage);
		}
		return stages;
	}

	public Map<PDGNode, Integer> mapNodesToStages() {
		for (int stage= 0; stage < stages.size(); stage++) {
			PipelineStage pipelineStage= stages.get(stage);
			List<PDGNode> selectedStatements= pipelineStage.getSelectedStatements();
			for (PDGNode node : selectedStatements) {
				node2stage.put(node, stage);
			}
		}
		return node2stage;
	}

	// For checking feasibility
	///////////////////////////

	// We take advantage of how WALA represents its dependencies using SSA form with Phi nodes 
	// to check for loop carried dependencies.
	// Basically if any of the stages (Stage1,...StageN) ever serve as an input dependence to 
	// the generator node then we have a loop carried dependency
	public boolean containsLoopCarriedDependency() {
		PipelineStage generator= getGenerator();
		List<DataDependence> inputDataDependences= generator.getInputDataDependences();
		for (DataDependence dependence : inputDataDependences) {
			PDGNode source= dependence.source;
			if (node2stage.get(source) != null) {
				return true; // We have this as one
			}
		}
		return false;
	}

	/**
	 * Sets up and starts the heap dependency analysis.
	 * 
	 * We intentionally separate heap analysis from scalar analysis because it is expensive. Thus we
	 * perform as much feasibility checks using intraprocedural checks first before turning this on.
	 * 
	 * @param callGraph
	 * @param pointerAnalysis
	 */
	public void computeHeapDependency(CallGraph callGraph, PointerAnalysis pointerAnalysis) {
		setupModRefInfrastructure(callGraph, pointerAnalysis);
		CGNode cgNode= getCurrentCGNode();
		for (PipelineStage stage : stages) {
			stage.computeHeapDependencies(cgNode, callGraph, pointerAnalysis, modref, mod, ref);

		}
	}

	// TODO: Investigate how we can get the correct context for the current CGNode. 
	//	Right now we are relying on the fact that there is a single context and using that
	private CGNode getCurrentCGNode() {
		MethodReference reference= pdg.getIr().getMethod().getReference();
		Set<CGNode> nodes= callGraph.getNodes(reference);

		Assertions.productionAssertion(nodes.size() == 1, "Expected a single corresponding CGNode, but got either 0 or more");

		CGNode node= nodes.iterator().next(); // Quick way to get first element of set with single entry since set doesn't implement get();
		return node;
	}

	private void setupModRefInfrastructure(CallGraph callGraph, PointerAnalysis pointerAnalysis) {
		this.callGraph= callGraph;
		this.modref= new AstJavaModRef();
		this.mod= modref.computeMod(callGraph, pointerAnalysis);
		this.ref= modref.computeRef(callGraph, pointerAnalysis);
	}

	// For querying
	///////////////

	/**
	 * 
	 * @return Number of stages including the generator stage
	 */
	public int getNumberOfStages() {
		return stages.size();
	}

	/**
	 * Retrieve the different stages of the pipeline
	 * 
	 * @param stageNumber - Numbering starts with Stage1, Stage2, etc.
	 * @return The stage corresponding to the number
	 */
	public PipelineStage getStage(int stageNumber) {
		if (stageNumber <= 0 || stageNumber >= stages.size())
			throw new IllegalArgumentException(String.format("We don't have stage %d. Stages are from 0 to %d", stageNumber, stages.size() - 1));
		return stages.get(stageNumber);
	}

	/**
	 * 
	 * @return The generator stage
	 */
	public PipelineStage getGenerator() {
		return stages.get(0);
	}
}
