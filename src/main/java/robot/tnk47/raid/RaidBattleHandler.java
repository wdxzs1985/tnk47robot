package robot.tnk47.raid;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.http.message.BasicNameValuePair;

import robot.tnk47.Tnk47EventHandler;
import robot.tnk47.Tnk47Robot;
import robot.tnk47.raid.model.RaidBattleDamageMap;
import robot.tnk47.raid.model.RaidBattleModel;
import robot.tnk47.raid.model.RaidItemModel;

public class RaidBattleHandler extends Tnk47EventHandler {

    private static final Pattern FEVER_PATTERN = Pattern.compile("みんなでフィーバー！");
    private static final Pattern HELP_PATTERN = Pattern.compile("助けを呼ぶ");

    private static final Pattern ITEM_PATTERN = Pattern.compile("tnk.pageParams.itemList.push\\((.*?)\\);");
    private static final Pattern TOTAL_POINT_PATTERN = Pattern.compile("<span class=\"totalPoint\">(\\d+)</span>");
    private static final Pattern FEVER_RATE_PATTERN = Pattern.compile("<span class=\"feverRateNum\">(\\d+)</span>");
    private static final Pattern SPECIAL_ATTACK_PATTERN = Pattern.compile("超全力秘薬を1/(\\d+)消費");
    private static final Pattern BOSS_HP_MATER_PATTERN = Pattern.compile("<span>HP</span><span class=\"mater\"><em style=\"width: (\\d{1,3})%;\">");

    private final boolean useRaidRegenItem;
    private final boolean useRaidSpecialAttack;
    private final RaidBattleDamageMap damageMap;

    public RaidBattleHandler(final Tnk47Robot robot,
            final RaidBattleDamageMap damageMap) {
        super(robot);
        this.useRaidRegenItem = robot.isUseRaidRegenItem();
        this.useRaidSpecialAttack = robot.isUseRaidSpecialAttack();
        this.damageMap = damageMap;
    }

    @Override
    public String handleIt() {
        final Map<String, Object> session = this.robot.getSession();
        final String raidBattleId = (String) session.get("raidBattleId");
        final String token = (String) session.get("token");
        final String path = String.format("/raid/raid-battle?raidBattleId=%s&token=%s",
                                          raidBattleId,
                                          token);
        final String html = this.httpGet(path);
        this.resolveInputToken(html);
        if (this.isRaid(html)) {
            final RaidBattleModel model = this.initModel(html);
            if (model.isHelpEnable()) {
                this.sleep();
                this.raidInvite();
            }
            if (model.isFeverEnable()) {
                this.sleep();
                final int feverRate = this.raidBattleFever();
                model.setFeverRate(feverRate);
            }

            if (model.isNoCost()) {
                model.setCanAttack(true);
                model.setFullPower(false);
                model.setSpecialAttack(false);
            } else if (!this.damageMap.isDamageEnough(raidBattleId)) {
                if (this.isUseRaidRegenItem()) {
                    this.useRaidRegenItem(model, model.getApCost());
                }
                if (model.isApEnough()) {
                    model.setCanAttack(true);
                    model.setFullPower(false);
                    model.setSpecialAttack(false);
                }
                if (model.isLimitedBoss() || model.isSpeedBoss()) {
                    // 大boss
                    if (this.isCanSpecialAttack(model)) {
                        model.setCanAttack(true);
                        model.setFullPower(false);
                        model.setSpecialAttack(true);
                    } else if (this.isCanFullAttack(model)) {
                        if (this.isUseRaidRegenItem()) {
                            this.useRaidRegenItem(model, model.getMaxAp());
                        }
                        if (model.isApFull()) {
                            model.setCanAttack(true);
                            model.setFullPower(true);
                            model.setSpecialAttack(false);
                        }
                    }
                }
            }

            if (model.isCanAttack()) {
                if (this.log.isInfoEnabled()) {
                    this.log.info(String.format("HP: %d/%d (%d%%)",
                                                model.getCurrentHp(),
                                                model.getMaxHp(),
                                                model.getBossHpPercent()));
                    if (model.isSpecialAttack()) {
                        this.log.info("超全力攻撃");
                    } else if (model.isFullPower()) {
                        this.log.info("全力攻撃");
                    } else {
                        this.log.info(String.format("AP: %d/%d USE: %d",
                                                    model.getApNow(),
                                                    model.getMaxAp(),
                                                    model.getApCost()));
                    }
                }

                session.put("deckId", model.getDeckId());
                session.put("isFullPower", String.valueOf(model.isFullPower()));
                session.put("isSpecialAttack",
                            String.valueOf(model.isSpecialAttack()));
                session.put("useApSmall",
                            String.valueOf(model.getApSmall().getUseCount()));
                session.put("useApFull",
                            String.valueOf(model.getApFull().getUseCount()));
                session.put("usePowerHalf",
                            String.valueOf(model.getPowerHalf().getUseCount()));
                session.put("usePowerFull",
                            String.valueOf(model.getPowerFull().getUseCount()));
                return "/raid/battle-animation";
            }
        } else if (this.isRaidResult(html)) {
            return "/raid/battle-result";
        }
        return "/raid";
    }

    private void useRaidRegenItem(final RaidBattleModel model, final int maxAp) {
        this.useRaidRegenItem(model, model.getApSmall(), maxAp);
        this.useRaidRegenItem(model, model.getApFull(), maxAp);
        this.useRaidRegenItem(model, model.getPowerHalf(), maxAp);
        this.useRaidRegenItem(model, model.getPowerFull(), maxAp);
    }

    private void useRaidRegenItem(final RaidBattleModel model,
                                  final RaidItemModel itemModel,
                                  final int maxAp) {
        int apNow = model.getApNow();
        final int regenValue = itemModel.getRegenValue();
        int itemCount = itemModel.getItemCount();
        int useCount = itemModel.getUseCount();
        while (itemCount > 0 && maxAp >= apNow + regenValue) {
            apNow += regenValue;
            itemCount--;
            useCount++;
        }
        model.setApNow(apNow);
        itemModel.setItemCount(itemCount);
        itemModel.setUseCount(useCount);
    }

    private boolean isCanFullAttack(final RaidBattleModel model) {
        boolean canFullAttack = true;
        canFullAttack = canFullAttack && model.canFullAttack();
        canFullAttack = canFullAttack && !model.isHpFull();
        return canFullAttack;
    }

    private boolean isCanSpecialAttack(final RaidBattleModel model) {
        boolean canSpecialAttack = this.isUseRaidSpecialAttack();
        canSpecialAttack = canSpecialAttack && model.canSpecialAttack();
        canSpecialAttack = canSpecialAttack && model.hasSpecialAttack();
        return canSpecialAttack;
    }

    private int getSpecialAttack(final String html) {
        final Matcher matcher = RaidBattleHandler.SPECIAL_ATTACK_PATTERN.matcher(html);
        while (matcher.find()) {
            final int specialAttack = Integer.valueOf(matcher.group(1));
            return specialAttack;
        }
        return 0;
    }

    private RaidBattleModel initModel(final String html) {
        final Map<String, Object> session = this.robot.getSession();
        final RaidBattleModel model = new RaidBattleModel();

        model.setRaidBossType((Integer) session.get("raidBossType"));
        model.setMaxHp((Long) session.get("maxHp"));
        model.setMinDamage((Long) session.get("minDamage"));
        model.setBossHpPercent(this.getBossHpPercent(html));

        model.setHelpEnable(this.isHelpEnable(html));
        model.setFeverEnable(this.isFeverEnable(html));

        model.setDeckAttack(this.getTotalPoint(html));
        model.setFeverRate(this.getFeverRate(html));

        final JSONObject pageParams = this.resolvePageParams(html);
        model.setDeckId(pageParams.optString("selectedDeckId"));
        model.setApCost(pageParams.optInt("apCost"));
        model.setMaxAp(pageParams.optInt("maxAp"));
        model.setApNow(pageParams.optInt("apNow"));

        final JSONArray itemList = pageParams.optJSONArray("itemList");
        for (int i = 0; i < itemList.size(); i++) {
            final JSONObject item = itemList.optJSONObject(i);
            final String itemId = item.optString("itemId");
            final String imgPath = item.optString("imgPath");
            final boolean isOneDay = StringUtils.contains(imgPath, "oneday");
            final int regenValue = item.optInt("regenValue");
            final int itemCount = item.optInt("itemCount");

            RaidItemModel itemModel = null;
            if (StringUtils.equals(itemId, "2967")) {
                itemModel = model.getApSmall();
                itemModel.setItemCount(itemCount);
                itemModel.setRegenValue(regenValue);
            } else if (StringUtils.equals(itemId, "8")) {
                if (isOneDay) {
                    itemModel = model.getPowerHalf();
                    itemModel.setItemCount(itemCount);
                    itemModel.setRegenValue(regenValue);
                }
            } else if (StringUtils.equals(itemId, "20")) {
                if (isOneDay) {
                    itemModel = model.getPowerFull();
                    itemModel.setItemCount(itemCount);
                    itemModel.setRegenValue(regenValue);
                }
            }
        }

        final RaidItemModel specialAttackItem = model.getSpecialAttack();
        specialAttackItem.setItemCount(this.getSpecialAttack(html));
        specialAttackItem.setRegenValue(0);
        specialAttackItem.setUseCount(0);
        return model;
    }

    private boolean isHelpEnable(final String html) {
        final Matcher matcher = RaidBattleHandler.HELP_PATTERN.matcher(html);
        return matcher.find();
    }

    private boolean isFeverEnable(final String html) {
        final Matcher matcher = RaidBattleHandler.FEVER_PATTERN.matcher(html);
        return matcher.find();
    }

    private boolean isRaid(final String html) {
        final String title = this.getHtmlTitle(html);
        return StringUtils.contains(title, "ボス対戦");
    }

    private boolean isRaidResult(final String html) {
        final String title = this.getHtmlTitle(html);
        return StringUtils.contains(title, "討伐結果");
    }

    private int getBossHpPercent(final String html) {
        final Matcher matcher = RaidBattleHandler.BOSS_HP_MATER_PATTERN.matcher(html);
        while (matcher.find()) {
            final int bossHpPercent = Integer.valueOf(matcher.group(1));
            return bossHpPercent;
        }
        return 0;
    }

    private int getTotalPoint(final String html) {
        int totalPoint = 0;
        final Matcher matcher = RaidBattleHandler.TOTAL_POINT_PATTERN.matcher(html);
        if (matcher.find()) {
            final int point = Integer.valueOf(matcher.group(1));
            if (totalPoint < point) {
                totalPoint = point;
            }
        }
        return totalPoint;
    }

    private int getFeverRate(final String html) {
        int feverRate = 0;
        final Matcher matcher = RaidBattleHandler.FEVER_RATE_PATTERN.matcher(html);
        while (matcher.find()) {
            feverRate = Integer.valueOf(matcher.group(1));
        }
        return feverRate;
    }

    private int raidBattleFever() {
        final Map<String, Object> session = this.robot.getSession();
        final String raidBattleId = (String) session.get("raidBattleId");
        final String token = (String) session.get("token");
        final String path = "/raid/ajax/put-raid-battle-fever";
        final List<BasicNameValuePair> nvps = this.createNameValuePairs();
        nvps.add(new BasicNameValuePair("raidBattleId", raidBattleId));
        nvps.add(new BasicNameValuePair("token", token));

        final JSONObject jsonResponse = this.httpPostJSON(path, nvps);
        this.resolveJsonToken(jsonResponse);

        int feverRate = 0;
        final JSONObject data = jsonResponse.optJSONObject("data");
        if (data != null) {
            final JSONObject feverUpDetailDto = data.optJSONObject("feverUpDetailDto");
            feverRate = feverUpDetailDto.optInt("rate");
            if (this.log.isInfoEnabled()) {
                final String displayUpRate = feverUpDetailDto.optString("displayUpRate");
                this.log.info(String.format("全員攻撃力 %s UP", displayUpRate));
            }
        }
        return feverRate;
    }

    private void raidInvite() {
        final Map<String, Object> session = this.robot.getSession();
        final String raidBattleId = (String) session.get("raidBattleId");
        final String token = (String) session.get("token");
        final String path = "/raid/ajax/put-raid-battle-invite";
        final List<BasicNameValuePair> nvps = this.createNameValuePairs();
        nvps.add(new BasicNameValuePair("raidBattleId", raidBattleId));
        nvps.add(new BasicNameValuePair("isToNation", String.valueOf(true)));
        nvps.add(new BasicNameValuePair("token", token));

        final JSONObject jsonResponse = this.httpPostJSON(path, nvps);
        this.resolveJsonToken(jsonResponse);
        if (this.log.isInfoEnabled()) {
            final JSONObject data = jsonResponse.optJSONObject("data");
            if (data != null) {
                final String resultMessage = data.optString("resultMessage");
                this.log.info(resultMessage);
            }
        }
    }

    @Override
    protected JSONObject resolvePageParams(final String html) {
        final JSONObject pageParams = super.resolvePageParams(html);
        final JSONArray itemList = pageParams.optJSONArray("itemList");
        final Matcher matcher = RaidBattleHandler.ITEM_PATTERN.matcher(html);
        while (matcher.find()) {
            final String itemData = matcher.group(1);
            itemList.add(JSONObject.fromObject(itemData));
        }
        return pageParams;
    }

    public boolean isUseRaidRegenItem() {
        return this.useRaidRegenItem;
    }

    public boolean isUseRaidSpecialAttack() {
        return this.useRaidSpecialAttack;
    }
}
