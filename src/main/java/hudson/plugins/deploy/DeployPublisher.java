package hudson.plugins.deploy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.listeners.ItemListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import jenkins.util.io.FileBoolean;

/**
 * Deploys WAR to a container.
 * 
 * @author Kohsuke Kawaguchi
 */
public class DeployPublisher extends Notifier {
	private List<ContainerDeploy> deploys;

	@DataBoundConstructor
	public DeployPublisher(ArrayList<ContainerDeploy> deploys) {
		this.deploys = deploys != null ? new ArrayList<ContainerDeploy>(deploys) : new ArrayList<ContainerDeploy>();
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		for (ContainerDeploy deploy : deploys) {
			if (build.getResult().equals(Result.SUCCESS) || deploy.onFailure) {
				for (FilePath warFile : build.getWorkspace().list(deploy.war)) {
					for (ContainerAdapter adapter : deploy.getAdapters())
						if (!adapter.redeploy(warFile, deploy.contextPath, build, launcher, listener))
							build.setResult(Result.FAILURE);
				}
			}
		}

		return true;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}

	/**
	 * Get the value of the adapterWrappers property
	 *
	 * @return The value of adapterWrappers
	 */
	public List<ContainerDeploy> getDeploys() {
		return deploys;
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public String getDisplayName() {
			return Messages.DeployPublisher_DisplayName();
		}

		/**
		 * Sort the descriptors so that the order they are displayed is more
		 * predictable
		 */
		public List<ContainerAdapterDescriptor> getAdaptersDescriptors() {
			List<ContainerAdapterDescriptor> r = new ArrayList<ContainerAdapterDescriptor>(ContainerAdapter.all());
			Collections.sort(r, new Comparator<ContainerAdapterDescriptor>() {
				public int compare(ContainerAdapterDescriptor o1, ContainerAdapterDescriptor o2) {
					return o1.getDisplayName().compareTo(o2.getDisplayName());
				}
			});
			return r;
		}
	}

	private static final long serialVersionUID = 1L;

	@Restricted(NoExternalUse.class)
	@Extension
	public static final class Migrator extends ItemListener {

		@SuppressWarnings("deprecation")
		@Override
		public void onLoaded() {
			FileBoolean migrated = new FileBoolean(getClass(), "migratedCredentials");
			if (migrated.isOn()) {
				return;
			}
			List<StandardUsernamePasswordCredentials> generatedCredentials = new ArrayList<StandardUsernamePasswordCredentials>();
			for (AbstractProject<?, ?> project : Jenkins.getActiveInstance().getAllItems(AbstractProject.class)) {
				try {
					DeployPublisher d = project.getPublishersList().get(DeployPublisher.class);
					if (d == null) {
						continue;
					}
					for (ContainerDeploy deploy : d.getDeploys()) {
						boolean modified = false;
						boolean successful = true;
						for (ContainerAdapter a : deploy.getAdapters()) {
							if (a instanceof PasswordProtectedAdapterCargo) {
								PasswordProtectedAdapterCargo ppac = (PasswordProtectedAdapterCargo) a;
								if (ppac.getCredentialsId() == null) {
									successful &= ppac.migrateCredentials(generatedCredentials);
									modified = true;
								}
							}
						}
						if (modified) {
							if (successful) {
								Logger.getLogger(DeployPublisher.class.getName()).log(Level.INFO,
										"Successfully migrated DeployPublisher in project: {0}", project.getName());
								project.save();
							} else {
								// Avoid calling project.save() because
								// PasswordProtectedAdapterCargo will null out
								// the
								// username/password fields upon saving
								Logger.getLogger(DeployPublisher.class.getName()).log(Level.SEVERE,
										"Failed to create credentials and migrate DeployPublisher in project: {0}, please manually add credentials.",
										project.getName());
							}
						}
					}
				} catch (IOException e) {
					Logger.getLogger(DeployPublisher.class.getName()).log(Level.WARNING, "Migration unsuccessful", e);
				}
			}
			migrated.on();
		}
	}

}
