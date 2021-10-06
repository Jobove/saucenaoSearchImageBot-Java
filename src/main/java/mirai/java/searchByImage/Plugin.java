package mirai.java.searchByImage;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.Listener;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.MessageChainBuilder;
import net.mamoe.mirai.message.data.QuoteReply;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Plugin extends JavaPlugin {
    public static final Plugin INSTANCE = new Plugin();

    Listener<GroupMessageEvent> listener;
    Settings settings;

    private Plugin() {
        super(new JvmPluginDescriptionBuilder("mirai.java.searchByImage.plugin", "1.0")
                .name("searchByImage-ver.Java")
                .info("A mirai plugin that support image searching using saucenao API - Java version.")
                .author("Jobove")
                .build());
    }

    @Override
    public void onEnable() {
        getLogger().info("ImageSearching bot loaded!");
        settings = checkSettings();
        if (settings != null)
            listener = GlobalEventChannel.INSTANCE.subscribeAlways(GroupMessageEvent.class,
                    groupMessageEvent -> {
                        MessageChain chain = groupMessageEvent.getMessage();
                        String messageString = chain.contentToString();
                        String reSimp = "以图搜图[ \\n]*?\\[图片]";
                        String reComp = "以图搜图[ \\n]*?\\[图片][ \\n]*?(\\d\\d?\\.\\d\\d?|\\d\\d?)";
                        Pattern patternSimp = Pattern.compile(reSimp, Pattern.MULTILINE | Pattern.DOTALL);
                        Matcher matcherSimp = patternSimp.matcher(messageString);
                        Pattern patternComp = Pattern.compile(reComp, Pattern.MULTILINE | Pattern.DOTALL);
                        Matcher matcherComp = patternComp.matcher(messageString);

                        if (matcherComp.find()) {
                            Image image = (Image) chain.stream().filter(Image.class::isInstance).findFirst().orElse(null);
                            double threshold = Double.parseDouble(matcherComp.group(1));
                            if (image != null) {
                                MessageChain messageChain = new MessageChainBuilder()
                                        .append(new QuoteReply(groupMessageEvent.getSource()))
                                        .append(image).append(searchBySaucenao(Image.queryUrl(image), threshold))
                                        .build();
                                groupMessageEvent.getGroup().sendMessage(messageChain);
                            }
                        } else if (matcherSimp.find()) {
                            Image image = (Image) chain.stream().filter(Image.class::isInstance).findFirst().orElse(null);
                            if (image != null) {
                                MessageChain messageChain = new MessageChainBuilder()
                                        .append(new QuoteReply(groupMessageEvent.getSource()))
                                        .append(image).append(searchBySaucenao(Image.queryUrl(image), 80.0)).build();

                                groupMessageEvent.getGroup().sendMessage(messageChain);
                            }
                        }
                    });
    }

    private String getHttpResult(String queryUrl) {
        HttpURLConnection connection = null;
        InputStream is = null;
        BufferedReader br = null;
        String result = null;
        try {
            URL url = new URL(queryUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.connect();

            if (connection.getResponseCode() == 200) {
                is = connection.getInputStream();
                br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String tmp;
                while ((tmp = br.readLine()) != null) {
                    sb.append(tmp);
                    sb.append("\r\n");
                }
                result = sb.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    @NotNull
    private String searchBySaucenao(String imageUrl, double threshold) {
        String apiKey = settings.getApiKey();
        String queryUrl = "https://saucenao.com/search.php?db=999&output_type=2&numres=16&api_key=" + apiKey + "&url=" + imageUrl;
        String result = getHttpResult(queryUrl);
        StringBuilder returnString = new StringBuilder();
        JSONObject jsonObject = JSON.parseObject(result);
        JSONArray resultArray = jsonObject.getJSONArray("results");

        int answerCount = 0;
        for (int i = 0; i < resultArray.size(); ++i) {

            JSONObject resultItem = resultArray.getJSONObject(i);
            JSONArray urlArray = resultItem.getJSONObject("data").getJSONArray("ext_urls");
            double similarity = resultItem.getJSONObject("header").getDouble("similarity");

            if (urlArray == null)
                continue;
            if (similarity < threshold)
                break;
            if (answerCount >= 5)
                break;
            if (answerCount == 0) {
                returnString.append(String.format("在以下来源找到相似度大于%.2f%%的结果:\n", threshold));
            }
            returnString.append(String.format("相似度：%.2f%%，链接：%s", similarity, urlArray.getString(0)));
            ++answerCount;
            if (answerCount < 5)
                returnString.append('\n');
        }
        if (answerCount == 0) {
            returnString.append(String.format("未在任何来源中寻找到相似度大于%.2f%%的结果。", threshold));
        }
        return returnString.toString();
    }

    @Nullable
    private Settings checkSettings() {
        boolean returnValue = true;

        //如果设置文件所在文件夹不存在, 则生成
        File settingsFolder = new File("config/searchByImage");
        if (!settingsFolder.exists() || !settingsFolder.isDirectory()) {
            returnValue = settingsFolder.mkdirs();
            getLogger().warning("Making directory");
        }

        //如果设置文件不存在, 则生成, 并写入默认的空设置
        File settingFile = new File("config/searchByImage/config.json");
        if (!settingFile.exists() || !settingFile.isFile()) {
            try {
                getLogger().warning("Creating config file");
                returnValue = settingFile.createNewFile();

                Settings emptySetting = new Settings();
                FileOutputStream configFileOutputStream = new FileOutputStream(settingFile);
                OutputStreamWriter configFileOutputStreamWriter = new OutputStreamWriter(configFileOutputStream);
                configFileOutputStreamWriter.append(JSON.toJSONString(emptySetting));
                configFileOutputStreamWriter.close();
                configFileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        StringBuilder configStringBuilder = new StringBuilder();
        try {
            FileInputStream configFileInputStream = new FileInputStream(settingFile);
            InputStreamReader configFileInputStreamReader = new InputStreamReader(configFileInputStream);
            while (configFileInputStreamReader.ready()) {
                configStringBuilder.append((char) configFileInputStreamReader.read());
            }
            configFileInputStreamReader.close();
            configFileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String configString = configStringBuilder.toString();

        Settings settings = JSON.parseObject(configString, Settings.class);

        if (settings.getApiKey().length() != 40) {
            getLogger().warning("程序配置文件错误, ApiKey长度不等于40!");
            returnValue = false;
        }

        if (returnValue) {
            return settings;
        } else {
            return null;
        }
    }
}

class Settings {
    @JSONField(name = "apiKey")
    private String apiKey = "";

    @JSONField(name = "apiKey")
    public String getApiKey() {
        return apiKey;
    }

    @JSONField(name = "apiKey")
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}