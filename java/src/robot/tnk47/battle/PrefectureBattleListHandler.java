package robot.tnk47.battle;

import java.util.List;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.http.message.BasicNameValuePair;

import robot.Robot;

public class PrefectureBattleListHandler extends AbstractBattleHandler {

    public PrefectureBattleListHandler(final Robot robot) {
        super(robot);
    }

    @Override
    protected String handleIt() {
        final Map<String, Object> session = this.robot.getSession();
        final String prefectureId = (String) session.get("prefectureId");
        final String path = "/battle/ajax/get-prefecture-battle-list";
        final List<BasicNameValuePair> nvps = this.createNameValuePairs();
        nvps.add(new BasicNameValuePair("prefectureId", prefectureId));
        nvps.add(new BasicNameValuePair("searchType", "3"));
        final JSONObject jsonResponse = this.httpPostJSON(path, nvps);
        final JSONObject data = jsonResponse.getJSONObject("data");
        final JSONObject prefectureBattleSystemDto = data.getJSONObject("prefectureBattleSystemDto");
        final String prefectureBattleSystemStatus = prefectureBattleSystemDto.getString("prefectureBattleSystemStatus");
        if (StringUtils.equals(prefectureBattleSystemStatus, "ACTIVE")) {
            final JSONArray prefectureBattleOutlines = data.getJSONArray("prefectureBattleOutlines");
            if (prefectureBattleOutlines.size() > 0) {
                final JSONObject battle = this.filterBattle(prefectureBattleOutlines);
                final String prefectureBattleId = battle.getString("prefectureBattleId");
                session.put("prefectureBattleId", prefectureBattleId);
                if (this.log.isInfoEnabled()) {
                    final String ownPrefectureName = battle.getString("ownPrefectureName");
                    final String otherPrefectureName = battle.getString("otherPrefectureName");
                    final int ownMemberCount = battle.getInt("ownMemberCount");
                    final int otherMemberCount = battle.getInt("otherMemberCount");
                    final int ownPrefectureHpRate = battle.getInt("ownPrefectureHPRate");
                    final int otherPrefectureHpRate = battle.getInt("otherPrefectureHPRate");
                    final int ownPrefectureHp = battle.getInt("ownPrefectureHp");
                    final int ownPrefectureHpLast = ownPrefectureHp * ownPrefectureHpRate
                                                    / 100;
                    final int otherPrefectureHp = battle.getInt("otherPrefectureHp");
                    final int otherPrefectureHpLast = otherPrefectureHp * otherPrefectureHpRate
                                                      / 100;
                    this.log.info(String.format("%s (共%d人参战，HP:%d/%d) vs %s (共%d人参战，HP:%d/%d)",
                                                ownPrefectureName,
                                                ownMemberCount,
                                                ownPrefectureHpLast,
                                                ownPrefectureHp,
                                                otherPrefectureName,
                                                otherMemberCount,
                                                otherPrefectureHpLast,
                                                otherPrefectureHp));
                }
                return "/battle/detail";
            } else {
                if (this.log.isInfoEnabled()) {
                    this.log.info("没有合战情报");
                }
                final JSONArray prefectureBattleUsers = data.getJSONArray("prefectureBattleUsers");
                if (prefectureBattleUsers.size() > 0) {
                    final JSONObject enemy = this.filterLowPowerUser(prefectureBattleUsers);
                    final String enemyId = enemy.getString("userId");
                    session.put("battleStartType", "1");
                    session.put("enemyId", enemyId);
                    if (this.log.isInfoEnabled()) {
                        final String enemyName = enemy.getString("userName");
                        this.log.info("向" + enemyName + "发动攻击");
                    }
                    return "/battle/battle-check";
                }
            }
        }
        return "/mypage";
    }

    private JSONObject filterBattle(final JSONArray outlines) {
        for (int i = 0; i < outlines.size(); i++) {
            final JSONObject battle = outlines.getJSONObject(i);
            if (battle.getBoolean("isInvite")) {
                if (this.log.isInfoEnabled()) {
                    this.log.info("收到救援信息");
                }
                return battle;
            }
        }
        // TODO 按时间排序
        // TODO 按人数排序
        // TODO 按HP排序
        return outlines.getJSONObject(0);
    }

    private JSONObject filterLowPowerUser(final JSONArray users) {
        int minDefencePower = Integer.MAX_VALUE;
        JSONObject minDefencePowerUser = null;
        for (int i = 0; i < users.size(); i++) {
            final JSONObject user = users.getJSONObject(i);
            final int defencePower = user.getInt("defencePower");
            if (minDefencePower > defencePower) {
                minDefencePowerUser = user;
                minDefencePower = defencePower;
            }
        }
        return minDefencePowerUser;
    }
}
