/**
 * This class derives from https://github.com/reprogrammer/keshmesh/ and is licensed under Illinois Open Source License.
 */
package edu.illinois.jflow.wala.pointeranalysis;

import com.ibm.wala.analysis.reflection.JavaTypeContext;
import com.ibm.wala.analysis.typeInference.PointType;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.util.intset.EmptyIntSet;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;

/**
 * Modified from KObjectSensitiveContextSelector.java, originally from Keshmesh. Authored by Mohsen
 * Vakilian and Stas Negara. Modified by Nicholas Chen.
 * 
 * Uses a combination of k-object sensitivity, javatypecontext and callersitecontext
 * 
 */
public class JFlowCustomContextSelector implements ContextSelector {

	public static final int K= 3;

	public static final ContextKey RECEIVER_STRING= new ContextKey() {
		@Override
		public String toString() {
			return "RECEIVER_STRING_KEY";
		}
	};

	@Override
	public Context getCalleeTarget(CGNode caller, CallSiteReference site, IMethod callee, InstanceKey[] actualParameters) {
		if (site.isStatic()) {
			// Static method
			return Everywhere.EVERYWHERE;
		}

		InstanceKey receiver= actualParameters[0];

		if (AnalysisUtils.isLibraryClass(callee.getDeclaringClass()) || (AnalysisUtils.isJDKClass(callee.getDeclaringClass()) && !AnalysisUtils.isObjectGetClass(callee))) {
			//Note: new Random() and similar statements cause an infinite pointer analysis for contexts like CallerSiteContext(caller, site)
			PointType pointType= new PointType(receiver.getConcreteType());
			return new JavaTypeContext(pointType);
//		} else if (AnalysisUtils.isAnnotatedFactoryMethod(callee)) {
//			return new CallerSiteContext(caller, site);
		} else {
			ReceiverString receiverString;
			if (!(caller.getContext() instanceof ReceiverStringContext)) {
				receiverString= new ReceiverString(receiver);
			} else {
				ReceiverString callerReceiverString= (ReceiverString)((ReceiverStringContext)caller.getContext()).get(RECEIVER_STRING);
				receiverString= new ReceiverString(receiver, K, callerReceiverString);
			}
			return new ReceiverStringContext(receiverString);
		}
	}

	private static final IntSet receiver= IntSetUtil.make(new int[] { 0 });

	@Override
	public IntSet getRelevantParameters(CGNode caller, CallSiteReference site) {
		if (site.isDispatch() || site.getDeclaredTarget().getNumberOfParameters() > 0) {
			return receiver;
		} else {
			return EmptyIntSet.instance;
		}
	}
}
