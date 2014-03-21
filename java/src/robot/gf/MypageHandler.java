package robot.gf;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

public class MypageHandler extends GFEventHandler {

    private static final Pattern HTML_USER_NAME_PATTERN = Pattern.compile("<h1><a href=\"/profile\">(.*?)</a></h1>");
    private static final Pattern HTML_JOB_CARD_SETTING_PATTERN = Pattern.compile("/job/job-card-setting");
    private static final Pattern HTML_JOB_FINISH_PATTERN = Pattern.compile("<a id=\"finishJobBtn\" class=\"btnPink\">受け取る</a>");
    private static final Pattern HTML_RAID_WAR_PATTERN = Pattern.compile("/raidwar\\?eventId=(\\d+)");
    private static final Pattern HTML_POST_URL = Pattern.compile("var POST_URL = '(.*?)'");
    private static final Pattern HTML_NEXT_URL = Pattern.compile("var NEXT_URL = '(.*?)'");

    public MypageHandler(final GFRobot robot) {
        super(robot);
    }

    @Override
    public String handleIt() {
        final Map<String, Object> session = this.robot.getSession();
        final String html = this.httpGet("/mypage");

        this.resolveJavascriptToken(html);

        if (!this.is("isMypage")) {
            final Matcher userNameMatcher = MypageHandler.HTML_USER_NAME_PATTERN.matcher(html);
            if (userNameMatcher.find()) {
                final String userName = userNameMatcher.group(1);
                this.log.info(String.format("角色： %s", userName));
                session.put("isMypage", true);
            } else {
                final String title = this.getHtmlTitle(html);
                if (this.log.isInfoEnabled()) {
                    this.log.info(title);
                }
                if (StringUtils.contains(title, "メンテナンスのお知らせ")) {
                    return "/exit";
                }

                String html2 = html;
                while (true) {
                    final String postURL = this.getPostURL(html2);
                    final String nextURL = this.getNextURL(html2);
                    if (StringUtils.isNotBlank(postURL)) {
                        html2 = this.httpGet(postURL);
                    } else if (StringUtils.isNotBlank(nextURL)) {
                        html2 = this.httpGet(nextURL);
                    } else {
                        break;
                    }
                }
                return "/mypage";
            }
        }

        if (this.is("isCupidEnable")) {
            session.put("isCupidEnable", false);
            return "/cupid";
        }

        if (this.is("isCupidStampEnable")) {
            session.put("isCupidStampEnable", false);
            return "/cupid/stamp";
        }

        if (this.is("isGiftEnable")) {
            session.put("isGiftEnable", false);
            return "/gift";
        }

        if (this.is("isUpgradeEnable")) {
            session.put("isUpgradeEnable", false);
            return "/upgrade";
        }

        if (this.is("isJobEnable")) {
            session.put("isJobEnable", false);
            if (MypageHandler.HTML_JOB_CARD_SETTING_PATTERN.matcher(html)
                                                           .find()) {
                return "/job/setting";
            }
            if (MypageHandler.HTML_JOB_FINISH_PATTERN.matcher(html).find()) {
                return "/job/payment";
            }
        }

        if (this.is("isRaidwarEnable")) {
            final Matcher matcher = MypageHandler.HTML_RAID_WAR_PATTERN.matcher(html);
            if (matcher.find()) {
                session.put("isRaidwarEnable", false);
                session.put("isQuestEnable", false);
                final String eventId = matcher.group(1);
                session.put("eventId", eventId);
                return "/raidwar";
            }
        }

        if (this.is("isQuestEnable")) {
            session.put("isQuestEnable", false);
            return "/quest";
        }
        return "/exit";
    }

    private String getPostURL(final String html) {
        final Matcher matcher = MypageHandler.HTML_POST_URL.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String getNextURL(final String html) {
        final Matcher matcher = MypageHandler.HTML_NEXT_URL.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
