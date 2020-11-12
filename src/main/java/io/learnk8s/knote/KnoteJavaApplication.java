package io.learnk8s.knote;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@SpringBootApplication
public class KnoteJavaApplication {

	public static void main(String[] args) {
		SpringApplication.run(KnoteJavaApplication.class, args);
	}
}

interface NotesRepository extends MongoRepository<Note, String> {

}

@Configuration
@EnableConfigurationProperties(KnoteProperties.class)
class KnoteConfig implements WebMvcConfigurer {

	private static final Logger LOGGER = LoggerFactory.getLogger(KnoteConfig.class);

	@Autowired
	private KnoteProperties properties;

	@PostConstruct
	private void init() {

		LOGGER.info("properties.getUploadDir()={}", properties.getUploadDir());
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry
		.addResourceHandler("/uploads/**")
		.addResourceLocations("file:" + properties.getUploadDir())
		.setCachePeriod(3600)
		.resourceChain(true)
		.addResolver(new PathResourceResolver());
	}
}

@ConfigurationProperties(prefix = "knote")
class KnoteProperties {
	@Value("${uploadDir:/tmp/uploads/}")
	private String uploadDir;

	public String getUploadDir() {
		return uploadDir;
	}
}

@Controller
class KNoteController {

	private static final Logger LOGGER = LoggerFactory.getLogger(KNoteController.class);

	@Autowired
	private NotesRepository notesRepository;
	@Autowired
	private KnoteProperties properties;

	private Parser parser = Parser.builder().build();
	private HtmlRenderer renderer = HtmlRenderer.builder().build();

	@GetMapping("/")
	public String index(Model model) {
		getAllNotes(model);
		return "index";
	}

	@PostMapping("/note")
	public String saveNotes(@RequestParam("image") MultipartFile file,
			@RequestParam String description,
			@RequestParam(required = false) String publish,
			@RequestParam(required = false) String upload,
			Model model) throws Exception {

		LOGGER.info("file={}", file);
		LOGGER.info("description={}", description);
		LOGGER.info("publish={}", publish);
		LOGGER.info("upload={}", upload);
		LOGGER.info("model={}", model);

		if (publish != null && publish.equals("Publish")) {

			saveNote(description, model);
			getAllNotes(model);

			return "redirect:/";

		} else if (upload != null && upload.equals("Upload")) {

			if (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isEmpty()) {

				uploadImage(file, description, model);
			}

			getAllNotes(model);

			return "index";
		}

		return "index";
	}

	private void getAllNotes(Model model) {

		List<Note> notes = notesRepository.findAll();

		Collections.reverse(notes);

		model.addAttribute("notes", notes);
	}

	private void uploadImage(MultipartFile file, String description, Model model) throws Exception {

		File uploadsDir = new File(properties.getUploadDir());

		if (!uploadsDir.exists()) {
			uploadsDir.mkdir();
		}

		LOGGER.info("uploadsDir.getAbsolutePath()={}", uploadsDir.getAbsolutePath());

		String fileId = UUID.randomUUID().toString() + "." + file.getOriginalFilename().split("\\.")[1];

		LOGGER.info("fileId={}", fileId);

		//file.transferTo(new File(properties.getUploadDir() + fileId));
		file.transferTo(new File(uploadsDir, fileId));

		model.addAttribute("description", description + " ![](/uploads/" + fileId + ")");
	}

	private void saveNote(String description, Model model) {

		LOGGER.info("description={}", description);

		if (description != null && !description.trim().isEmpty()) {

			//We need to translate markup to HTML
			Node document = parser.parse(description.trim());

			LOGGER.info("document={}", document);

			String html = renderer.render(document).trim();

			LOGGER.info("html={}", html);

			Note note = new Note(null, html);

			LOGGER.info("note={}", note);

			notesRepository.save(note);
			
			LOGGER.info("note={}", note);

			//After publish you need to clean up the textarea
			model.addAttribute("description", "");
		}
	}
}
