package robot.tnk47.gacha;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.message.BasicNameValuePair;

import robot.tnk47.Tnk47Robot;

public class StampGachaHandler extends AbstractGachaHandler {

    private static final Pattern DISABLE_PATTHERN = Pattern.compile("<p class=\"actBtn remainingTime disable\"><span>(.*?)</span></p>");
    private static final Pattern ACTBTN_PATTHERN = Pattern.compile("<a href=\"(/gacha/gacha-free-animation\\?.*?)\" class=\"actBtn jscTouchActive \" ?>");

    public StampGachaHandler(final Tnk47Robot robot) {
        super(robot);
    }

    @Override
    public String handleIt() {
        final List<BasicNameValuePair> nvps = this.createNameValuePairs();
        final String html = this.httpPost("/gacha/stamp-gacha", nvps);
        final Matcher actBtnMatcher = StampGachaHandler.ACTBTN_PATTHERN.matcher(html);
        if (actBtnMatcher.find()) {
            String url = actBtnMatcher.group(1);
            url = StringEscapeUtils.unescapeHtml(url);
            this.openGachaAnimation(url);
        } else {
            if (this.log.isInfoEnabled()) {
                final Matcher disableMatcher = StampGachaHandler.DISABLE_PATTHERN.matcher(html);
                if (disableMatcher.find()) {
                    final String reason = disableMatcher.group(1);
                    this.log.info(reason);
                }
            }
        }
        return "/mypage";
    }

    private void openGachaAnimation(final String path) {
        final String html = this.httpGet(path);
        this.resolveGachaResult(html);
    }

}
