package biz.aQute.resolve;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.felix.resolver.Logger;
import org.apache.felix.resolver.ResolverImpl;
import org.osgi.framework.ServiceReference;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.service.log.LogService;
import org.osgi.service.resolver.ResolutionException;

import aQute.bnd.build.Container;
import aQute.bnd.build.Project;
import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;
import aQute.bnd.osgi.resource.ResourceUtils.IdentityCapability;
import aQute.bnd.service.Strategy;
import biz.aQute.resolve.internal.BndrunResolveContext;

/**
 * This class provides resolving capabilities to a Project (and this a bndrun
 * which is a Run which extends Project). This class is supposed to simplify the
 * sometimes bewildering number of moving cogs in resolving. It is a processor
 * and uses the facilities to provide the different logging schemes used.
 */

public class ProjectResolver extends Processor implements ResolutionCallback {

	private final class ReporterLogger extends Logger implements LogService {
		ReporterLogger(int i) {
			super(i);
		}

		@Override
		protected void doLog(int level, String msg, Throwable throwable) {
			String format = throwable == null ? "%s" : "%s: %s";
			switch (level) {
				case 0 : // error
					error(format, msg, throwable);
					break;

				case 1 :
					warning(format, msg, throwable);
					break;

				default :
					trace(format, msg, throwable);
					break;
			}
		}

		@Override
		public void log(ServiceReference sr, int level, String message) {
			doLog(level, toString(sr) + message, null);

		}

		@Override
		public void log(ServiceReference sr, int level, String message, Throwable exception) {
			doLog(level, toString(sr) + message, exception);
		}

		private String toString(ServiceReference< ? > sr) {
			return "[" + sr.getProperty(org.osgi.framework.Constants.SERVICE_ID) + "] ";
		}
	}

	private Project							project;
	private Map<Resource,List<Wire>>		resolution;
	private ReporterLogger					log			= new ReporterLogger(0);
	private ResolverImpl					resolver	= new ResolverImpl(new ReporterLogger(0));
	private ResolveProcess					resolve		= new ResolveProcess();
	private Collection<ResolutionCallback>	cbs			= new ArrayList<ResolutionCallback>();

	public ProjectResolver(Project project) {
		super(project);
		getSettings(project);
		this.project = project;
	}

	public Map<Resource,List<Wire>> resolve() throws ResolutionException {
		resolution = resolve.resolveRequired(project, project, resolver, cbs, log);
		return resolution;
	}

	@Override
	public void processCandidates(Requirement requirement, Set<Capability> wired, List<Capability> candidates) {
		// System.out.println("Process candidates " + requirement + " " + wired
		// + " " + candidates);
	}

	/**
	 * Get the run bundles from the resolution. Resolve if this has not happened
	 * yet.
	 */

	public List<Container> getRunbundles() throws Exception {
		if (resolution == null)
			resolve();

		List<Container> containers = new ArrayList<Container>();
		for (Resource r : resolution.keySet()) {
			IdentityCapability identity = ResourceUtils.getIdentityCapability(r);
			if (identity == null) {
				error("Identity for %s not found", r);
				continue;
			}

			Container bundle = project.getBundle(identity.osgi_identity(), identity.version().toString(),
					Strategy.EXACT, null);
			if (bundle == null) {
				error("Bundle for %s-%s not found", identity.osgi_identity(), identity.version());
				continue;
			}

			containers.add(bundle);
		}
		return containers;
	}

	/**
	 * Validate the current project for resolving.
	 */

	public void validate() {
		BndrunResolveContext context = getContext();
		String runrequires = project.getProperty(RUNREQUIRES);
		if (runrequires == null || runrequires.isEmpty()) {
			error("Requires the %s instruction to be set", RUNREQUIRES);
		} else {
			if (EMPTY_HEADER.equals(runrequires))
				return;

			exists(context, runrequires, "Initial requirement %s cannot be resolved to an entry in the repositories");
		}
		String framework = project.getProperty(RUNFW);
		if (framework == null) {
			error("No framework is set");
		} else {
			exists(context, framework, "Framework not found");
		}
	}

	private void exists(BndrunResolveContext context, String framework, String msg) {
		Parameters p = new Parameters(framework);
		for (Map.Entry<String,Attrs> e : p.entrySet()) {
			exists(context, e.getKey(), e.getValue(), msg);
		}
	}

	private void exists(BndrunResolveContext context, String namespace, Attrs attrs, String msg) {
		Requirement req = CapReqBuilder.getRequirementFrom(namespace, attrs);
		List<Capability> caps = context.findProviders(req);
		if (caps == null || caps.isEmpty())
			error(msg, req);
	}

	public BndrunResolveContext getContext() {
		return new BndrunResolveContext(project, project, log);
	}

}
