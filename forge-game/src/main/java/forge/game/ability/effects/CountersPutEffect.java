package forge.game.ability.effects;

import com.google.common.collect.Lists;
import forge.game.GameObject;
import forge.game.ability.AbilityUtils;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.card.CounterType;
import forge.game.card.CardPredicates.Presets;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.util.Aggregates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class CountersPutEffect extends SpellAbilityEffect {
    @Override
    protected String getStackDescription(SpellAbility sa) {
        final StringBuilder sb = new StringBuilder();
        final Card card = sa.getHostCard();
        final boolean dividedAsYouChoose = sa.hasParam("DividedAsYouChoose");


        final int amount = AbilityUtils.calculateAmount(card, sa.getParam("CounterNum"), sa);
        if (sa.hasParam("Bolster")) {
            sb.append("Bolster ").append(amount);
            return sb.toString();
        }
        if (dividedAsYouChoose) {
            sb.append("Distribute ");
        } else {
            sb.append("Put ");
        }
        if (sa.hasParam("UpTo")) {
            sb.append("up to ");
        }

        sb.append(amount).append(" ");

        String type = sa.getParam("CounterType");
        if (type.equals("ExistingCounter")) {
            sb.append("of an existing counter");
        } else {

            sb.append( CounterType.valueOf(type).getName()).append(" counter");
        }
        if (amount != 1) {
            sb.append("s");
        }
        if (dividedAsYouChoose) {
            sb.append(" among ");
        } else {
            sb.append(" on ");
        }
        final List<Card> tgtCards = getTargetCards(sa);

        final Iterator<Card> it = tgtCards.iterator();
        while (it.hasNext()) {
            final Card tgtC = it.next();
            if (tgtC.isFaceDown()) {
                sb.append("Morph");
            } else {
                sb.append(tgtC);
            }

            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append(".");

        return sb.toString();
    }

    @Override
    public void resolve(SpellAbility sa) {
        final Card card = sa.getHostCard();
        final Player activator = sa.getActivatingPlayer();

        String strTyp = sa.getParam("CounterType");
        CounterType counterType = null;
        boolean existingCounter = strTyp.equals("ExistingCounter");
        boolean eachExistingCounter = sa.hasParam("EachExistingCounter");
        String amount = sa.getParamOrDefault("CounterNum", "1");

        if (!existingCounter) {
            try {
                counterType = AbilityUtils.getCounterType(strTyp, sa);
            } catch (Exception e) {
                System.out.println("Counter type doesn't match, nor does an SVar exist with the type name.");
                return;
            }
        }

        final boolean etbcounter = sa.hasParam("ETB");
        final boolean remember = sa.hasParam("RememberCounters");
        final boolean rememberCards = sa.hasParam("RememberCards");
        int counterAmount = AbilityUtils.calculateAmount(sa.getHostCard(), amount, sa);
        final int max = sa.hasParam("MaxFromEffect") ? Integer.parseInt(sa.getParam("MaxFromEffect")) : -1;

        if (sa.hasParam("UpTo")) {
            counterAmount = activator.getController().chooseNumber(sa, "How many counters?", 0, counterAmount);
        }

        CardCollection tgtCards = new CardCollection();
        List<GameObject> tgtObjects = Lists.newArrayList();
        if (sa.hasParam("Bolster")) {
            CardCollection creatsYouCtrl = CardLists.filter(activator.getCardsIn(ZoneType.Battlefield), Presets.CREATURES);
            CardCollection leastToughness = new CardCollection(Aggregates.listWithMin(creatsYouCtrl, CardPredicates.Accessors.fnGetDefense));
            tgtCards.addAll(activator.getController().chooseCardsForEffect(leastToughness, sa, "Choose a creature with the least toughness", 1, 1, false));
            tgtObjects.addAll(tgtCards);
        } else {
            tgtObjects.addAll(getDefinedOrTargeted(sa, "Defined"));
        }

        for (final GameObject obj : tgtObjects) {
            if (existingCounter) {
                final List<CounterType> choices = new ArrayList<CounterType>();
                if (obj instanceof Card) {
                    for (final CounterType ct : ((Card) obj).getCounters().keySet()) {
                        if (((Card) obj).getCounters(ct) > 0) {
                            choices.add(ct);
                        }
                    }
                }
                else if (obj instanceof Player) {
                    for (final CounterType ct : ((Player) obj).getCounters().keySet()) {
                        if (((Player) obj).getCounters(ct) > 0) {
                            choices.add(ct);
                        }
                    }
                }

                if (choices.isEmpty()) {
                    continue;
                }

                if (eachExistingCounter) {
                    for(CounterType ct : choices) {
                        if (obj instanceof Player) {
                            ((Player) obj).addCounter(ct, counterAmount, true);

                        }
                        if (obj instanceof Card) {
                            ((Card) obj).addCounter(ct, counterAmount, true);
                        }
                    }
                    continue;
                } else {
                    counterType = choices.size() == 1 ? choices.get(0) : activator.getController().chooseCounterType(choices, sa, "Select counter type to add");
                }
            }


            if (obj instanceof Card) {
                Card tgtCard = (Card) obj;
                counterAmount = sa.usesTargeting() && sa.hasParam("DividedAsYouChoose") ? sa.getTargetRestrictions().getDividedValue(tgtCard) : counterAmount;
                if (!sa.usesTargeting() || tgtCard.canBeTargetedBy(sa)) {
                    if (max != -1) {
                        counterAmount = Math.max(Math.min(max - tgtCard.getCounters(counterType), counterAmount), 0);
                    }
                    if (sa.hasParam("Tribute")) {
                        String message = "Do you want to put " + counterAmount + " +1/+1 counters on " + tgtCard + " ?";
                        Player chooser = activator.getController().chooseSingleEntityForEffect(activator.getOpponents(), sa, "Choose an opponent");
                        if (chooser.getController().confirmAction(sa, PlayerActionConfirmMode.Tribute, message)) {
                            tgtCard.setTributed(true);
                        } else {
                            continue;
                        }
                    }
                    if (rememberCards) {
                        card.addRemembered(tgtCard);
                    }
                    final Zone zone = tgtCard.getGame().getZoneOf(tgtCard);
                    if (zone == null || zone.is(ZoneType.Battlefield) || zone.is(ZoneType.Stack)) {
                        tgtCard.addCounter(counterType, counterAmount, true);
                        if (remember) {
                            final int value = tgtCard.getTotalCountersToAdd();
                            tgtCard.addCountersAddedBy(card, counterType, value);
                        }

                        if (sa.hasParam("Evolve")) {
                            final HashMap<String, Object> runParams = new HashMap<String, Object>();
                            runParams.put("Card", tgtCard);
                            tgtCard.getController().getGame().getTriggerHandler().runTrigger(TriggerType.Evolved, runParams, false);
                        }
                        if (sa.hasParam("Monstrosity")) {
                            tgtCard.setMonstrous(true);
                            tgtCard.setMonstrosityNum(counterAmount);
                            final HashMap<String, Object> runParams = new HashMap<String, Object>();
                            runParams.put("Card", tgtCard);
                            tgtCard.getController().getGame().getTriggerHandler().runTrigger(TriggerType.BecomeMonstrous, runParams, false);
                        }
                        if (sa.hasParam("Renown")) {
                            tgtCard.setRenowned(true);
                            final HashMap<String, Object> runParams = new HashMap<String, Object>();
                            runParams.put("Card", tgtCard);
                            tgtCard.getController().getGame().getTriggerHandler().runTrigger(TriggerType.BecomeRenowned, runParams, false);
                        }
                    } else {
                        // adding counters to something like re-suspend cards
                        // etbcounter should apply multiplier
                        tgtCard.addCounter(counterType, counterAmount, etbcounter);
                    }
                }
            } else if (obj instanceof Player) {
                // Add Counters to players!
                Player pl = (Player) obj;
                pl.addCounter(counterType, counterAmount, true);
            }
        }
    }

}
