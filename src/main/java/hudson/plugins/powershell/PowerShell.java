package hudson.plugins.powershell;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.CommandInterpreter;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.SystemUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Invokes PowerShell from Jenkins.
 * 
 * @author Kohsuke Kawaguchi
 */
public class PowerShell extends CommandInterpreter {

    /** boolean switch setting -NoProfile */
    private final boolean useProfile;

    private Integer unstableReturn;

    private final boolean stopOnError;

    @DataBoundConstructor
    public PowerShell(String command, boolean stopOnError, boolean useProfile, Integer unstableReturn) {
        super(command);
        this.stopOnError = stopOnError;
        this.useProfile = useProfile;
        this.unstableReturn = unstableReturn;
    }

    public boolean isStopOnError() {
        return stopOnError;
    }

    public boolean isUseProfile() {
        return useProfile;
    }

    protected String getFileExtension() {
        return ".ps1";
    }

    @CheckForNull
    public final Integer getUnstableReturn() {
        return Integer.valueOf(0).equals(unstableReturn) ? null : unstableReturn;
    }

    @DataBoundSetter
    public void setUnstableReturn(Integer unstableReturn) {
        this.unstableReturn = unstableReturn;
    }

    @Override
    protected boolean isErrorlevelForUnstableBuild(int exitCode) {
        return this.unstableReturn != null && exitCode != 0 && this.unstableReturn.equals(exitCode);
    }

    public String[] buildCommandLine(FilePath script) {
        if (isRunningOnWindows(script)) {
            if (useProfile){
                return new String[] { "powershell.exe", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.getRemote()};
            } else {
                return new String[] { "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.getRemote()};
            }
        } else {
            // ExecutionPolicy option does not work (and is not required) for non-Windows platforms
            // See https://github.com/PowerShell/PowerShell/issues/2742
            if (useProfile){
                return new String[] { "pwsh", "-NonInteractive", "-File", script.getRemote()};
            } else {
                return new String[] { "pwsh", "-NonInteractive", "-NoProfile", "-File", script.getRemote()};
            }
        }
    }

    protected String getContents() {
        StringBuilder sb = new StringBuilder();
        if (stopOnError) {
            sb.append("$ErrorActionPreference=\"Stop\"");
        } else {
            sb.append("$ErrorActionPreference=\"Continue\"");
        }
        sb.append(System.lineSeparator());
        sb.append(command);
        sb.append(System.lineSeparator());
        sb.append("exit $LastExitCode");
        return sb.toString();
    }

    private boolean isRunningOnWindows(FilePath script) {
        // Ideally this would use a property of the build/run, but unfortunately CommandInterpreter only gives us the
        // FilePath, so we need to guess based on that.
        if (!script.isRemote()) {
            // Running locally, so we can just check the local OS
            return SystemUtils.IS_OS_WINDOWS;
        }

        // Running remotely, guess based on the path. A path starting with something like "C:\" is Windows.
        String path = script.getRemote();
        return path.length() > 3 && path.charAt(1) == ':' && path.charAt(2) == '\\';
    }

    @Extension @Symbol("powerShell")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public String getHelpFile() {
            return "/plugin/powershell/help.html";
        }
        
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getDisplayName() {
            return "PowerShell";
        }
    }
}
