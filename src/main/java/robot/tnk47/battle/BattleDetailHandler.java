package robot.tnk47.battle;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.http.message.BasicNameValuePair;

import robot.tnk47.Tnk47Robot;

public class BattleDetailHandler extends AbstractBattleHandler {

    private static final Pattern BATTLE_POINT_PATTERN = Pattern.compile("<span class=\"nowGetPoint\">(\\d+)pt</span>");
    private static final Pattern BATTLE_POINT_INFO_PATTERN = Pattern.compile("<span class=\"addPointInfo\">報酬確定!勝利を目指せ!</span>");
    private static final Pattern BATTLE_INVITE_PATTERN = Pattern.compile("救援依頼を出す");

    private final int minBattlePoint;
    private final int battlePointFilter;

    public BattleDetailHandler(final Tnk47Robot robot) {
        super(robot);
        this.minBattlePoint = robot.getMinBattlePoint();
        this.battlePointFilter = robot.getBattlePointFilter();
    }

    @Override
    protected String handleIt() {
        final Map<String, Object> session = this.robot.getSession();
        final String prefectureBattleId = (String) session.get("prefectureBattleId");
        final String path = String.format("/battle?prefectureBattleId=%s",
                                          prefectureBattleId);
        final String html = this.httpGet(path);

        if (this.isBattleResult(html)) {
            return "/battle/prefecture-battle-result";
        }

        this.resolveInputToken(html);

        this.sendInvite(html);

        final JSONObject jsonPageParams = this.resolvePageParams(html);
        if (jsonPageParams != null) {
            final String battleStartType = jsonPageParams.optString("battleStartType");
            session.put("battleStartType", battleStartType);
            final JSONObject userData = jsonPageParams.optJSONObject("userData");
            final JSONObject data = userData.optJSONObject("data");

            final JSONObject supportFriend = this.findSupportFriend(data);
            if (supportFriend != null) {
                final String supportUserId = supportFriend.optString("userId");
                this.sendSupport(supportUserId);
            }
            if (this.minBattlePoint == 0) {
                final Matcher matcher = BattleDetailHandler.BATTLE_POINT_INFO_PATTERN.matcher(html);
                if (matcher.find()) {
                    if (this.log.isInfoEnabled()) {
                        this.log.info("报酬确定,停止攻击。");
                    }
                    session.put("isBattlePointEnough", true);
                    return "/mypage";
                }
            } else {
                final Matcher matcher = BattleDetailHandler.BATTLE_POINT_PATTERN.matcher(html);
                if (matcher.find()) {
                    final int battlePoint = Integer.valueOf(matcher.group(1));
                    if (battlePoint > this.minBattlePoint) {
                        if (this.log.isInfoEnabled()) {
                            this.log.info("报酬确定,停止攻击。");
                        }
                        session.put("isBattlePointEnough", true);
                        return "/mypage";
                    }
                }
            }
            final JSONObject battleEnemy = this.findBattleEnemy(data);
            if (battleEnemy != null) {
                final String userId = battleEnemy.optString("userId");
                session.put("enemyId", userId);
                if (this.log.isInfoEnabled()) {
                    final String userName = battleEnemy.optString("userName");
                    final String userLevel = battleEnemy.optString("userLevel");
                    this.log.info(String.format("向 %s(%s) 发动攻击",
                                                userName,
                                                userLevel));
                }
                return "/battle/battle-check";
            }
        }
        return "/mypage";
    }

    private void sendInvite(final String html) {
        final Matcher inviteMatcher = BattleDetailHandler.BATTLE_INVITE_PATTERN.matcher(html);
        if (inviteMatcher.find()) {
            final Map<String, Object> session = this.robot.getSession();
            final String token = (String) session.get("token");
            final String path = "/battle/ajax/put-prefecture-battle-invite";
            final List<BasicNameValuePair> nvps = this.createNameValuePairs();
            nvps.add(new BasicNameValuePair("token", token));
            final JSONObject jsonResponse = this.httpPostJSON(path, nvps);
            this.resolveJsonToken(jsonResponse);
            if (this.log.isInfoEnabled()) {
                final JSONObject data = jsonResponse.optJSONObject("data");
                final String resultMessage = data.optString("resultMessage");
                this.log.info(resultMessage);
            }
        }
    }

    private JSONObject findSupportFriend(final JSONObject data) {
        final JSONArray friendData = data.optJSONArray("friendData");
        int maxUserLoseCount = 0;
        JSONObject supportFriend = null;
        for (int i = 0; i < friendData.size(); i++) {
            final JSONObject friend = friendData.optJSONObject(i);
            if (friend.optBoolean("canSupport", false)) {
                final int userLoseCount = friend.optInt("userLoseCount");
                if (maxUserLoseCount <= userLoseCount) {
                    supportFriend = friend;
                    maxUserLoseCount = userLoseCount;
                }
            }
        }
        return supportFriend;
    }

    private void sendSupport(final String supportUserId) {
        final Map<String, Object> session = this.robot.getSession();
        final String token = (String) session.get("token");
        final String path = "/battle/ajax/put-battle-support";
        final List<BasicNameValuePair> nvps = this.createNameValuePairs();
        nvps.add(new BasicNameValuePair("supportUserId", supportUserId));
        nvps.add(new BasicNameValuePair("token", token));
        final JSONObject jsonResponse = this.httpPostJSON(path, nvps);
        this.resolveJsonToken(jsonResponse);
        if (this.log.isInfoEnabled()) {
            final JSONObject data = jsonResponse.optJSONObject("data");
            final String supportUserName = data.optString("supportUserName");
            this.log.info(String.format("给%s发送了应援", supportUserName));
        }
    }

    private JSONObject findBattleEnemy(final JSONObject data) {
        final JSONArray enemyData = data.optJSONArray("enemyData");
        JSONObject battleEnemy = null;
        int maxBattlePoint = this.battlePointFilter;
        for (int i = 0; i < enemyData.size(); i++) {
            final JSONObject enemy = enemyData.optJSONObject(i);
            final String topIcons = enemy.optString("topIcons");
            final int getBattlePoint = enemy.optInt("getBattlePoint");
            if (StringUtils.contains(topIcons, "caution")) {
                if (this.log.isInfoEnabled()) {
                    this.log.info("！！！神壕出没，闪避！！！");
                }
            } else if (maxBattlePoint < getBattlePoint) {
                battleEnemy = enemy;
                maxBattlePoint = getBattlePoint;
            }
        }
        return battleEnemy;
    }
}
