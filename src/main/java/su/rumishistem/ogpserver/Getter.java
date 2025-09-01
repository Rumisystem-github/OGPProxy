package su.rumishistem.ogpserver;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;

import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.ogpserver.Type.OGP;
import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;
import su.rumishistem.rumi_java_lib.SANITIZE;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.SnowFlake;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;

public class Getter {
	private static ConcurrentHashMap<String, CompletableFuture<JsonNode>> ProgressMap = new ConcurrentHashMap<String, CompletableFuture<JsonNode>>();

	public static JsonNode Get(String url) throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException, SQLException {
		JsonNode Cache = getCache(url);
		if (Cache != null) return Cache;

		CompletableFuture<JsonNode> Future = ProgressMap.computeIfAbsent(url, Key->{
			CompletableFuture<JsonNode> F = new CompletableFuture<JsonNode>();

			CompletableFuture.runAsync(new Runnable() {
				@Override
				public void run() {
					try {
						LOG(LOG_TYPE.INFO, "取得:" + SANITIZE.CONSOLE_SANITIZE(url));

						LinkedHashMap<String, Object> site_info = new LinkedHashMap<String, Object>();
						site_info.put("TYPE", "WEB_SITE");
						site_info.put("SITE_NAME", new URL(url).getHost());
						site_info.put("TITLE", "");
						site_info.put("DESCRIPTION", "");
						site_info.put("COLOR", "FFFFFF");
						site_info.put("THUMBNAIL", new ArrayList<String>());

						FETCH Ajax = new FETCH(url);
						Ajax.SetHEADER("User-Agent", Main.UserAgent);
						FETCH_RESULT Result = Ajax.GET();

						if (Result.getHeader("Content-Type") != null) {
							String ContentType = Result.getHeader("Content-Type");
							LOG(LOG_TYPE.INFO, "コンテンツタイプ：" + SANITIZE.CONSOLE_SANITIZE(ContentType));

							if (ContentType.startsWith("image/")) {
								//画像単体である
								site_info.put("TYPE", "IMAGE");
								((ArrayList<String>)site_info.get("THUMBNAIL")).add(url);
							} else if (ContentType.startsWith("text/html")) {
								//HTML
								String body = Result.getString();

								//OGP
								OGP ogp = parse_ogp(body);
								if (ogp != null) {
									site_info.put("SITE_NAME", ogp.get_site_name());
									site_info.put("TITLE", ogp.get_title());
									site_info.put("DESCRIPTION", ogp.get_description());
									site_info.put("THUMBNAIL", ogp.get_image());
								}

								//テーマカラー
								String theme_color = parse_theme_color(body);
								if (theme_color != null) {
									site_info.put("COLOR", theme_color);
								}
							}
						}

						//キャッシュに書き込み
						String ID = String.valueOf(SnowFlake.GEN());

						SQL.UP_RUN("INSERT INTO `WEBSITE` (`ID`, `URL`, `UPDATE`) VALUES (?, ?, NOW())", new Object[] {
							ID, url
						});

						SQL.UP_RUN("INSERT INTO `INFO` (`WEBSITE`, `TYPE`, `SITE_NAME`, `TITLE`, `DESCRIPTION`, `COLOR`) VALUES (?, ?, ?, ?, ?, ?)", new Object[] {
							ID,
							site_info.get("TYPE"),
							site_info.get("SITE_NAME"),
							site_info.get("TITLE"),
							site_info.get("DESCRIPTION"),
							site_info.get("COLOR")
						});

						for (String ImageURL:((ArrayList<String>)site_info.get("THUMBNAIL"))) {
							SQL.UP_RUN("INSERT INTO `THUMBNAIL` (`ID`, `WEBSITE`, `URL`) VALUES (?, ?, ?)", new Object[] {
								String.valueOf(SnowFlake.GEN()),
								ID,
								ImageURL
							});
						}

						F.complete(getCache(url));
					} catch (Exception EX) {
						EX.printStackTrace();
						F.completeExceptionally(EX);
					} finally {
						ProgressMap.remove(url);
					}
				}
			});

			return F;
		});

		return Future.get();
	}

	private static OGP parse_ogp(String body) {
		boolean find = false;
		String site_name = "不明";
		String title = "不明";
		String description = "";
		List<String> image = new ArrayList<>();

		Matcher mtc = Pattern.compile("<META\\s+PROPERTY=[\"']OG:([a-zA-Z0-9:_\\-]+)[\"']\\s+CONTENT=[\"'](.*?)[\"']\\s*/?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(body);
		while (mtc.find()) {
			String key = mtc.group(1).toUpperCase();
			String val = mtc.group(2);
			find = true;

			switch (key) {
				case "SITE_NAME": {
					site_name = val;
					break;
				}

				case "TITLE": {
					title = val;
					break;
				}

				case "DESCRIPTION": {
					description = val;
					break;
				}

				case "IMAGE": {
					image.add(val);
					//((ArrayList<String>)OGP.get("THUMBNAIL")).add(Val);
					break;
				}
			}
		}

		if (find) {
			return new OGP(site_name, title, description, image);
		} else {
			return null;
		}
	}

	private static String parse_theme_color(String body) {
		Matcher mtc = Pattern.compile("<META\\s+NAME=[\"']THEME-COLOR[\"']\\s+CONTENT=[\"']#([0-9A-F]+)[\"']\\s*/?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(body);
		if (mtc.find()) {
			return mtc.group(1).toUpperCase();
		} else {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static JsonNode getCache(String URL) throws JsonMappingException, JsonProcessingException, SQLException {
		ArrayNode Result = SQL.RUN("""
			SELECT
				WEBSITE.UPDATE,
				INFO.TYPE,
				INFO.SITE_NAME,
				INFO.TITLE,
				INFO.DESCRIPTION,
				INFO.COLOR,
				(
					SELECT
						JSON_ARRAYAGG(`URL`)
					FROM
						`THUMBNAIL`
					WHERE
						`WEBSITE` = WEBSITE.ID
				) AS `THUMBNAIL`
			FROM
				`WEBSITE` AS WEBSITE
			LEFT JOIN
				`INFO` AS INFO
				ON INFO.WEBSITE = WEBSITE.ID
			LEFT JOIN
				`THUMBNAIL` AS THUMBNAIL
				ON THUMBNAIL.WEBSITE = WEBSITE.ID
			WHERE
				WEBSITE.URL = ?;
		""", new Object[] {
			URL
		});

		if (Result.length() == 1) {
			ArrayNode Row = Result.get(0);

			LinkedHashMap<String, Object> OGP = new LinkedHashMap<String, Object>();
			OGP.put("TYPE", Row.getData("TYPE").asString());
			OGP.put("SITE_NAME", Row.getData("SITE_NAME").asString());
			OGP.put("TITLE", Row.getData("TITLE").asString());
			OGP.put("DESCRIPTION", Row.getData("DESCRIPTION").asString());
			OGP.put("THUMBNAIL", new ArrayList<String>());
			OGP.put("COLOR", Row.getData("COLOR").asString());

			JsonNode Thumbnail = new ObjectMapper().readTree(Row.getData("THUMBNAIL").asString());
			for (int I = 0; I < Thumbnail.size(); I++) {
				((ArrayList<String>)OGP.get("THUMBNAIL")).add(Thumbnail.get(I).asText());
			}

			LOG(LOG_TYPE.INFO, "キャッシュ：" + SANITIZE.CONSOLE_SANITIZE(URL));
			return new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(OGP));
		} else {
			return null;
		}
	}
}
