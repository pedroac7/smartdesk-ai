package br.ufrn.smartdesk.aisupport;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartdesk")
public class AiSupportProperties {

	private final Ai ai = new Ai();

	private final Rag rag = new Rag();

	private final Services services = new Services();

	private final ExternalMcp externalMcp = new ExternalMcp();

	public Ai getAi() {
		return ai;
	}

	public Rag getRag() {
		return rag;
	}

	public Services getServices() {
		return services;
	}

	public ExternalMcp getExternalMcp() {
		return externalMcp;
	}

	public static class Ai {

		private String mode = "fake";

		private final Openai openai = new Openai();

		public String getMode() {
			return mode;
		}

		public void setMode(String mode) {
			this.mode = mode;
		}

		public Openai getOpenai() {
			return openai;
		}

		public boolean isOpenaiMode() {
			return "openai".equalsIgnoreCase(mode);
		}
	}

	public static class Openai {

		private String model = "";

		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}
	}

	public static class Rag {

		private String docsPath = "../rag-docs";

		public String getDocsPath() {
			return docsPath;
		}

		public void setDocsPath(String docsPath) {
			this.docsPath = docsPath;
		}
	}

	public static class Services {

		private final SupportRulesMcp supportRulesMcp = new SupportRulesMcp();

		public SupportRulesMcp getSupportRulesMcp() {
			return supportRulesMcp;
		}
	}

	public static class SupportRulesMcp {

		private String baseUrl = "";

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}
	}

	public static class ExternalMcp {

		private final Filesystem filesystem = new Filesystem();

		public Filesystem getFilesystem() {
			return filesystem;
		}
	}

	public static class Filesystem {

		private boolean enabled = true;

		private String command = "npx";

		private String packageName = "@modelcontextprotocol/server-filesystem";

		private String docsPath = "../external-mcp-docs";

		private Duration timeout = Duration.ofSeconds(5);

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getCommand() {
			return command;
		}

		public void setCommand(String command) {
			this.command = command;
		}

		public String getPackageName() {
			return packageName;
		}

		public void setPackageName(String packageName) {
			this.packageName = packageName;
		}

		public String getDocsPath() {
			return docsPath;
		}

		public void setDocsPath(String docsPath) {
			this.docsPath = docsPath;
		}

		public Duration getTimeout() {
			return timeout;
		}

		public void setTimeout(Duration timeout) {
			this.timeout = timeout;
		}
	}
}
