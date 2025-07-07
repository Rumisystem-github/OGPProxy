package su.rumishistem.ogpserver;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import su.rumishistem.rumi_java_lib.ArrayNode;
import su.rumishistem.rumi_java_lib.CONFIG;
import su.rumishistem.rumi_java_lib.FETCH;
import su.rumishistem.rumi_java_lib.FETCH_RESULT;
import su.rumishistem.rumi_java_lib.SANITIZE;
import su.rumishistem.rumi_java_lib.SQL;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_REQUEST;
import su.rumishistem.rumi_java_lib.SmartHTTP.HTTP_RESULT;
import su.rumishistem.rumi_java_lib.SmartHTTP.SmartHTTP;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointFunction;
import su.rumishistem.rumi_java_lib.SmartHTTP.Type.EndpointEntrie.Method;

public class Main {
	public static final String UserAgent = "RumiShistem/1.0 Bot/1.0";
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
					return new HTTP_RESULT(400, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}

				try {
					String URL = URLDecoder.decode(r.GetEVENT().getURI_PARAM().get("URL"));
					JsonNode Data = Getter.Get(URL);

					LinkedHashMap<String, Object> Return = new LinkedHashMap<String, Object>();
					Return.put("STATUS", true);
					Return.put("DATA", Data);
					return new HTTP_RESULT(200, new ObjectMapper().writeValueAsString(Return).getBytes(), "application/json; charset=UTF-8");
				} catch (Exception EX) {
					EX.printStackTrace();
					return new HTTP_RESULT(500, "{\"STATUS\": false}".getBytes(), "application/json; charset=UTF-8");
				}
			}
		});

		SH.Start();
	}
}
