package hudson.plugins.deploy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public class ContainerDeploy extends AbstractDescribableImpl<ContainerDeploy> implements ExtensionPoint, Serializable {
	private List<ContainerAdapter> adapters;
	public final String contextPath;

	public final String war;
	public final boolean onFailure;

	/**
	 * @deprecated Use {@link #getAdapters()}
	 */
	public final ContainerAdapter adapter = null;

	@DataBoundConstructor
	public ContainerDeploy(List<ContainerAdapter> adapters, String war, String contextPath, boolean onFailure) {
		this.adapters = adapters;
		this.war = war;
		this.onFailure = onFailure;
		this.contextPath = contextPath;
	}

	/**
	 * Get the value of the adapterWrappers property
	 *
	 * @return The value of adapterWrappers
	 */
	public List<ContainerAdapter> getAdapters() {
		return adapters;
	}

	public Object readResolve() {
		if (adapter != null) {
			if (adapters == null) {
				adapters = new ArrayList<ContainerAdapter>();
			}
			adapters.add(adapter);
		}
		return this;
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<ContainerDeploy> {
		public String getDisplayName() {
			return Messages.DeployPublisher_DisplayName();
		}
	}
}
