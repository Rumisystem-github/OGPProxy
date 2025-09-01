package su.rumishistem.ogpserver;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.CONFIG;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_REQUEST;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.SmartHTTP;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointFunction;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointEntrie.Method;

public class Main {
	public static final String UserAgent = "RumiShistem/1.0 OGPProxy/1.0";
	public static ArrayNode CONFIG_DATA = null;

	public static void main(String[] args) throws IOException, InterruptedException {
		//設定ファイルを読み込む
		if (new File("Config.ini").exists()) {
			CONFIG_DATA = new CONFIG().DATA;
			LOG(LOG_TYPE.PROCESS_END_OK, "");
		} else {
			LOG(LOG_TYPE.PROCESS_END_FAILED, "");
			LOG(LOG_TYPE.FAILED, "ERR! Config.ini ga NAI!!!!!!!!!!!!!!");
			System.exit(1);
		}

		//SQL
		SQL.CONNECT(
			CONFIG_DATA.get("SQL").getData("IP").asString(),
			CONFIG_DATA.get("SQL").getData("PORT").asString(),
			CONFIG_DATA.get("SQL").getData("DB").asString(),
			CONFIG_DATA.get("SQL").getData("USER").asString(),
			CONFIG_DATA.get("SQL").getData("PASS").asString()
		);

		SmartHTTP SH = new SmartHTTP(CONFIG_DATA.get("HTTP").getData("PORT").asInt());

		SH.SetRoute("/get", Method.GET, new EndpointFunction() {
			@Override
			public HTTP_RESULT Run(HTTP_REQUEST r) throws Exception {
				if (r.GetEVENT().getURI_PARAM().get("URL") == null) {
					return new HTTP_RESULT(400, "{\"STATUS\": false, \"ERR\":\"URL\"}".getBytes(), "application/json; charset=UTF-8");
				}

				try {
					String url = URLDecoder.decode(r.GetEVENT().getURI_PARAM().get("URL"));
					if (check_url(new URL(url))) {
						JsonNode Data = Getter.Get(url);

						LinkedHashMap<String, Object> Return = new LinkedHashMap<String, Object>();
						Return.put("STATUS", true);
						Return.put("DATA", Data);
						return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(Return).getBytes(), "application/json; charset=UTF-8");
					} else {
						return new HTTP_RESULT(400, "{\"STATUS\": false, \"ERR\":\"BAD_URL\"}".getBytes(), "application/json; charset=UTF-8");
					}
				} catch (Exception EX) {
					EX.printStackTrace();
					return new HTTP_RESULT(500, "{\"STATUS\": false, \"ERR\":\"SYSTEM_ERR\"}".getBytes(), "application/json; charset=UTF-8");
				}
			}
		});

		SH.Start();
	}

	private static boolean check_url(URL url) {
		try {
			String host = url.getHost();

			//localhost→ダメ
			if (host.equalsIgnoreCase("localhost")) return false;

			//宛先がプライベートIP→ダメ
			/*InetAddress address = InetAddress.getByName(host);
			if (address.isAnyLocalAddress()) return false;
			if (address.isLoopbackAddress()) return false;
			if (address.isSiteLocalAddress()) return false;
			if (address.isLinkLocalAddress()) return false;*/

			//問題のないURL
			return true;
		} catch (Exception EX) {
			//不正なURL
			return false;
		}
	}
}
