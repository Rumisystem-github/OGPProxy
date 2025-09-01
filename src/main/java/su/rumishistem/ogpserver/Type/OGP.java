package su.rumishistem.ogpserver.Type;

import java.util.List;

public class OGP {
	private String site_name;
	private String title;
	private String description;
	private List<String> image;

	public OGP(String site_name, String title, String description, List<String> image) {
		this.site_name = site_name;
		this.title = title;
		this.description = description;
		this.image = image;
	}

	public String get_site_name() {
		return site_name;
	}

	public String get_title() {
		return title;
	}

	public String get_description() {
		return description;
	}

	public List<String> get_image() {
		return image;
	}
}
