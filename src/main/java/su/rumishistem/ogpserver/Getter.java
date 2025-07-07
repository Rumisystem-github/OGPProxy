package su.rumishistem.ogpserver;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;
import su.rumishistem.rumi_java_lib.SANITIZE;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.SnowFlake;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;

public class Getter {
	private static ConcurrentHashMap<String, CompletableFuture<JsonNode>> ProgressMap = new ConcurrentHashMap<String, CompletableFuture<JsonNode>>();

	public static JsonNode Get(String URL) throws InterruptedException, ExecutionException, JsonMappingException, JsonProcessingException, SQLException {
		JsonNode Cache = getCache(URL);
		if (Cache != null) return Cache;

		CompletableFuture<JsonNode> Future = ProgressMap.computeIfAbsent(URL, Key->{
			CompletableFuture<JsonNode> F = new CompletableFuture<JsonNode>();

			CompletableFuture.runAsync(new Runnable() {
				@Override
				public void run() {
					try {
						LOG(LOG_TYPE.INFO, "取得:" + SANITIZE.CONSOLE_SANITIZE(URL));

						LinkedHashMap<String, Object> OGP = new LinkedHashMap<String, Object>();
						OGP.put("OGP", false);
						OGP.put("SITE_NAME", null);
						OGP.put("TITLE", null);
						OGP.put("DESCRIPTION", null);
						OGP.put("THUMBNAIL", new ArrayList<String>());

						FETCH Ajax = new FETCH(URL);
						Ajax.SetHEADER("User-Agent", Main.UserAgent);
						FETCH_RESULT Result = Ajax.GET();

						if (Result.getHeader("Content-Type") != null) {
							String ContentType = Result.getHeader("Content-Type");
							System.out.println("コンテンツタイプ：" + SANITIZE.CONSOLE_SANITIZE(ContentType));

							if (ContentType.startsWith("image/")) {
								//画像単体である
								((ArrayList<String>)OGP.get("THUMBNAIL")).add(URL);
							} else {
								//HTMLならOGPを読む
								if (ContentType.startsWith("text/html")) {
									String Body = Result.getString();

									Matcher MTC = Pattern.compile("<META\\s+PROPERTY=[\"']OG:([a-zA-Z0-9:_\\-]+)[\"']\\s+CONTENT=[\"'](.*?)[\"']\\s*/?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(Body);
									while (MTC.find()) {
										String Key = MTC.group(1).toUpperCase();
										String Val = MTC.group(2);
										OGP.put("OGP", true);

										switch (Key) {
											case "SITE_NAME": {
												OGP.put("SITE_NAME", Val);
												break;
											}

											case "TITLE": {
												OGP.put("TITLE", Val);
												break;
											}

											case "DESCRIPTION": {
												OGP.put("DESCRIPTION", Val);
												break;
											}

											case "IMAGE": {
												((ArrayList<String>)OGP.get("THUMBNAIL")).add(Val);
												break;
											}
										}
									}
								}
							}
						}

						//キャッシュに書き込み
						String ID = String.valueOf(SnowFlake.GEN());

						SQL.UP_RUN("INSERT INTO `WEBSITE` (`ID`, `URL`, `OGP`, `UPDATE`) VALUES (?, ?, ?, NOW())", new Object[] {
							ID, URL, (OGP.get("OGP") != null)
						});

						if ((boolean)OGP.get("OGP")) {
							SQL.UP_RUN("INSERT INTO `OGP` (`WEBSITE`, `SITE_NAME`, `TITLE`, `DESCRIPTION`) VALUES (?, ?, ?, ?)", new Object[] {
								ID,
								OGP.get("SITE_NAME"),
								OGP.get("TITLE"),
								OGP.get("DESCRIPTION")
							});
						}

						for (String ImageURL:((ArrayList<String>)OGP.get("THUMBNAIL"))) {
							SQL.UP_RUN("INSERT INTO `THUMBNAIL` (`ID`, `WEBSITE`, `URL`) VALUES (?, ?, ?)", new Object[] {
								String.valueOf(SnowFlake.GEN()),
								ID,
								ImageURL
							});
						}

						F.complete(getCache(URL));
					} catch (Exception EX) {
						EX.printStackTrace();
						F.completeExceptionally(EX);
					} finally {
						ProgressMap.remove(URL);
					}
				}
			});

			return F;
		});

		return Future.get();
	}

	private static JsonNode getCache(String URL) throws JsonMappingException, JsonProcessingException, SQLException {
		ArrayNode Result = SQL.RUN("""
			SELECT
				WEBSITE.UPDATE,
				WEBSITE.OGP,
				OGP.SITE_NAME AS `OGP_SITE_NAME`,
				OGP.TITLE AS `OGP_TITLE`,
				OGP.DESCRIPTION AS `OGP_DESCRIPTION`,
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
				`OGP` AS OGP
				ON OGP.WEBSITE = WEBSITE.ID
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
			OGP.put("OGP", Row.getData("OGP").asBool());
			OGP.put("SITE_NAME", null);
			OGP.put("TITLE", null);
			OGP.put("DESCRIPTION", null);
			OGP.put("THUMBNAIL", new ArrayList<String>());

			if (Row.getData("OGP").asBool()) {
				OGP.put("SITE_NAME", Row.getData("OGP_SITE_NAME").asString());
				OGP.put("TITLE", Row.getData("OGP_TITLE").asString());
				OGP.put("DESCRIPTION", Row.getData("OGP_DESCRIPTION").asString());
			}

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
