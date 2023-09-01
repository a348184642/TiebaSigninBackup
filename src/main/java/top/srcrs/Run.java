package top.srcrs;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.srcrs.util.Encryption;
import top.srcrs.util.Request;

import java.util.ArrayList;
import java.util.List;

public class Run {
    private static final Logger LOGGER = LoggerFactory.getLogger(Run.class);

    private static final String LIKE_URL = "https://tieba.baidu.com/mo/q/newmoindex";
    private static final String TBS_URL = "http://tieba.baidu.com/dc/common/tbs";
    private static final String SIGN_URL = "http://c.tieba.baidu.com/c/c/forum/sign";

    private final List<String> follow = new ArrayList<>();
    private static final List<String> success = new ArrayList<>();
    private String tbs = "";
    private static int followNum = 201;

    public static void main(String[] args) {
        Run run = new Run();
        run.initialize(args);
        run.runSign();
        LOGGER.info("Total: {} - Success: {} - Failed: {}", followNum, success.size(), followNum - success.size());
        if (args.length == 2) {
            run.send(args[1]);
        }
    }

    private void initialize(String[] args) {
        if (args.length == 0) {
            LOGGER.warn("Please provide BDUSS in Secrets.");
            return;
        }
        
        Cookie cookie = Cookie.getInstance();
        cookie.setBDUSS(args[0]);

        getTbs();
        getFollow();
    }

    private void getTbs() {
        try {
            JSONObject jsonObject = Request.get(TBS_URL);
            if ("1".equals(jsonObject.getString("is_login"))) {
                LOGGER.info("TBS retrieval successful");
                tbs = jsonObject.getString("tbs");
            } else {
                LOGGER.warn("TBS retrieval failed -- " + jsonObject);
            }
        } catch (Exception e) {
            LOGGER.error("Error while retrieving TBS -- " + e);
        }
    }

    private void getFollow() {
        try {
            JSONObject jsonObject = Request.get(LIKE_URL);
            LOGGER.info("Fetched follow list successfully");
            JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("like_forum");
            followNum = jsonArray.size();
            
            for (Object array : jsonArray) {
                String forumName = ((JSONObject) array).getString("forum_name");
                if ("0".equals(((JSONObject) array).getString("is_sign"))) {
                    follow.add(forumName.replace("+", "%2B"));
                } else {
                    success.add(forumName);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error while fetching follow list -- " + e);
        }
    }

    private void runSign() {
        int rounds = 5;
        try {
            while (success.size() < followNum && rounds > 0) {
                LOGGER.info("----- Round {} Sign-In Start -----", 5 - rounds + 1);
                LOGGER.info("{} forums left to sign in", followNum - success.size());
                
                for (String forum : follow) {
                    String rotation = forum.replace("%2B", "+");
                    String body = "kw=" + forum + "&tbs=" + tbs + "&sign=" + Encryption.enCodeMd5("kw=" + rotation + "tbs=" + tbs + "tiebaclient!!!");
                    JSONObject post = Request.post(SIGN_URL, body);
                    
                    if ("0".equals(post.getString("error_code"))) {
                        follow.remove(forum);
                        success.add(rotation);
                        LOGGER.info(rotation + ": Sign-in successful");
                    } else {
                        LOGGER.warn(rotation + ": Sign-in failed");
                    }
                }

                if (success.size() != followNum) {
                    Thread.sleep(1000 * 60 * 5);
                    getTbs();
                }
                
                rounds--;
            }
        } catch (Exception e) {
            LOGGER.error("Error during sign-in process -- " + e);
        }
    }

    private void send(String sckey) {
        String text = "Total: " + followNum + " - Success: " + success.size() + " - Failed: " + (followNum - success.size());
        String desp = "Total " + followNum + " forums\n\nSuccess: " + success.size() + " Failed: " + (followNum - success.size());
        String body = "text=" + text + "&desp=" + "TiebaSignIn Results\n\n" + desp;
        
        try {
            StringEntity entityBody = new StringEntity(body, "UTF-8");
            HttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost("https://sc.ftqq.com/" + sckey + ".send");
            httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
            httpPost.setEntity(entityBody);
            HttpResponse resp = client.execute(httpPost);
            
            if (resp.getStatusLine().getStatusCode() < 400) {
                HttpEntity entity = resp.getEntity();
                String respContent = EntityUtils.toString(entity, "UTF-8");
                LOGGER.info("Server notification sent successfully");
            } else {
                LOGGER.warn("Server notification failed");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to send server notification -- " + e);
        }
    }
}
