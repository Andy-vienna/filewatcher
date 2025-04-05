package org.andy.filewatcher;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class FileWatcher {

	private static final Logger logger = Logger.getLogger(FileWatcher.class.getName());

	private static Properties config = new Properties();
	private static Path folderToWatch = null;
	private static String fileExtension = null;

	//###################################################################################################################################################
	//###################################################################################################################################################

	public static void main(String[] args) {

		try {
			setupLogger();
			config = loadConfig();
		} catch (IOException e) {
			e.printStackTrace();
		}

		folderToWatch = Paths.get(config.getProperty("watch.path"));
		fileExtension = config.getProperty("watch.extension");

		try {
			new FileWatcher().startWatching();
			logger.info("File Watcher überwacht: " + folderToWatch);
		} catch (Exception e) {
			logger.severe("Fehler bei Start der Überwachung: " + e.getMessage());
		}

	}

	//###################################################################################################################################################
	//###################################################################################################################################################

	public void startWatching() throws IOException, InterruptedException {

		WatchService watchService = FileSystems.getDefault().newWatchService();
		folderToWatch.register(watchService, ENTRY_CREATE);

		while (true) {
			WatchKey key = watchService.take();

			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind<?> kind = event.kind();
				if (kind == OVERFLOW) {
					continue;
				}

				@SuppressWarnings("unchecked")
				WatchEvent<Path> ev = (WatchEvent<Path>) event;

				Path fileName = ev.context();
				String sFileName = fileName.toString().toLowerCase();

				if (sFileName.endsWith(fileExtension)) {
					Path fullPath = folderToWatch.resolve(fileName);

					boolean bWaitSmo = isLocked(sFileName);
					while(bWaitSmo) {
						Thread.sleep(1000);
						bWaitSmo = isLocked(sFileName);
					}

					try {
						String sResult = openFile(config.getProperty("file.viewer"), fullPath.toString());
						if (sResult != null) {
							logger.warning(sResult);
						} else {
							logger.info("Datei erkannt und geöffnet: " + extractFileName(fullPath.toString()));
						}
					} catch (IOException e) {
						logger.severe("Fehler beim öffnen der Datei: " + e.getMessage());
					}

				}
			}

			if (!key.reset()) {
				logger.info("File Watcher beendet.");
				break;
			}
		}
	}

	//###################################################################################################################################################
	//###################################################################################################################################################

	private static void setupLogger() throws IOException {
		FileHandler fileHandler = new FileHandler("filewatcher.log", true); // true = anhängen
		fileHandler.setFormatter(new SimpleFormatter()); // einfache Textausgabe
		logger.addHandler(fileHandler);
		logger.setLevel(Level.WARNING); // ab Warnung loggen
		logger.setUseParentHandlers(true); // true = auch in Konsole ausgeben
	}

	private static Properties loadConfig() throws IOException {
		Properties props = new Properties();

		// Pfad zum aktuellen .jar-Ordner ermitteln
		String jarPath = FileWatcher.class
				.getProtectionDomain()
				.getCodeSource()
				.getLocation()
				.getPath();

		// Entferne führenden Slash (falls vorhanden)
		if (jarPath.startsWith("/")) {
			jarPath = jarPath.substring(1);
		}

		Path jarDir = Paths.get(jarPath).getParent();
		Path configPath = jarDir.resolve("config.properties");

		try (InputStream in = Files.newInputStream(configPath)) {
			props.load(in);
		}

		return props;
	}

	private static boolean isLocked(String fileName) {
		try (RandomAccessFile randomAccessFile = new RandomAccessFile(fileName, "rw");
				FileLock lock = randomAccessFile.getChannel().lock()) {
			return lock == null;
		} catch (IOException ex) {
			return true;
		}
	}

	private static String extractFileName(String filePath) {
		return java.nio.file.Paths.get(filePath).getFileName().toString();
	}

	private boolean isArtifact(String fileName) {
		return fileName.matches(".*\\.(tmp|log|dat|lck|smo|pdf)?");
	}

	private String openFile(String viewerPath, String filePath) throws IOException {
		File file = new File(filePath);

		if (!file.exists() || file.length() == 0) {
			return "Datei existiert nicht oder ist leer: " + filePath;
		}

		List<String> command = Arrays.asList(viewerPath, filePath);
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(new File(System.getProperty("java.io.tmpdir")));

		pb.start(); // Datei mit angegebenem Viewer öffnen

		File dir = new File(System.getProperty("user.dir"));
		for (File f : dir.listFiles()) {
			if (f.isFile() && f.length() == 0 && isArtifact(f.getName())) {
				f.delete(); // Artefakte falls vorhanden löschen
			}
		}
		return null;
	}

}

