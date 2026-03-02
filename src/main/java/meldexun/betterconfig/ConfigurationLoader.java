package meldexun.betterconfig;

import java.io.File;
import java.io.IOException;

public class ConfigurationLoader {

	public static Config load(File file, Class<?> type) throws IOException {
		return new Config(file.toPath(), type);
	}

	public static void save(Config cfg, File file) throws IOException {
		cfg.save(file.toPath());
	}

}
