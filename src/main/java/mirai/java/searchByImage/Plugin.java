package mirai.java.searchByImage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.*;
import com.alibaba.fastjson.*;
import com.alibaba.fastjson.annotation.JSONField;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.Listener;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.*;

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
        if(settings != null)
            listener = GlobalEventChannel.INSTANCE.subscribeAlways(GroupMessageEvent.class, groupMessageEvent -> {
            MessageChain chain = groupMessageEvent.getMessage();
            String messageString = chain.contentToString();
            String re = "以图搜图";
            Pattern pattern = Pattern.compile(re, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(messageString);

            if (matcher.find()) {
                Image image = (Image) chain.stream().filter(Image.class::isInstance).findFirst().orElse(null);
                if (image != null) {
                    MessageChain messageChain = new MessageChainBuilder().append(new QuoteReply(groupMessageEvent.getSource())).append(image).append(searchBySaucenao(Image.queryUrl(image))).build();
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
    private String searchBySaucenao(String imageUrl) {
        String apiKey = settings.getApiKey();
        String queryUrl = "https://saucenao.com/search.php?db=999&output_type=2&numres=16&api_key=" + apiKey + "&url=" + imageUrl;
        String result = getHttpResult(queryUrl);
        StringBuilder returnString = new StringBuilder();
        boolean hasAnswer = false;

        JSONObject jsonObject = JSON.parseObject(result);
        JSONArray resultArray = jsonObject.getJSONArray("results");
        for (int i = 0; i < resultArray.size(); ++i) {
//            JSONObject resultItem = JSON.parseObject(o.toString());
            JSONObject resultItem = resultArray.getJSONObject(i);
            JSONArray urlArray = resultItem.getJSONObject("data").getJSONArray("ext_urls");
            double similarity = resultItem.getJSONObject("header").getDouble("similarity");
            if(urlArray == null)
                continue;
            if (similarity < 80)
                break;
            if(!hasAnswer) {
                returnString.append("在以下来源找到相似度大于80%的结果:\n");
                hasAnswer = true;
            }
            returnString.append("相似度：").append(similarity).append("，链接：").append(urlArray.getString(0)).append('\n');
        }
        if(!hasAnswer) {
            returnString.append("未在任何来源中寻找到相似度大于80%的结果。");
        }
        return returnString.toString();
    }

    @Nullable
    private Settings checkSettings() {
        boolean returnValue = true;

        //如果设置文件所在文件夹不存在, 则生成
        File settingsFolder = new File("config/searchByImage");
        if(!settingsFolder.exists() || !settingsFolder.isDirectory()) {
            returnValue = settingsFolder.mkdirs();
            getLogger().warning("Making directory");
        }

        //如果设置文件不存在, 则生成, 并写入默认的空设置
        File settingFile = new File("config/searchByImage/config.json");
        if(!settingFile.exists() || !settingFile.isFile()) {
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
            while(configFileInputStreamReader.ready()) {
                configStringBuilder.append((char) configFileInputStreamReader.read());
            }
            configFileInputStreamReader.close();
            configFileInputStream.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
        String configString = configStringBuilder.toString();

        Settings settings = JSON.parseObject(configString, Settings.class);

        if(settings.getApiKey().length() != 40) {
            getLogger().warning("程序配置文件错误, ApiKey长度不等于40!");
            returnValue = false;
        }

        if(returnValue) {
            return settings;
        }
        else {
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