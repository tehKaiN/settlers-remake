package jsettlers.logic.buildings.military;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import jsettlers.common.CommonConstants;
import jsettlers.common.buildings.EBuildingType;
import jsettlers.common.buildings.IBuilding;
import jsettlers.common.buildings.IBuildingOccupyer;
import jsettlers.common.buildings.OccupyerPlace;
import jsettlers.common.buildings.OccupyerPlace.ESoldierType;
import jsettlers.common.map.shapes.FreeMapArea;
import jsettlers.common.map.shapes.MapCircle;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.mapobject.IAttackableTowerMapObject;
import jsettlers.common.material.ESearchType;
import jsettlers.common.movable.EMovableType;
import jsettlers.common.movable.IMovable;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.logic.algorithms.path.IPathCalculateable;
import jsettlers.logic.algorithms.path.Path;
import jsettlers.logic.algorithms.path.dijkstra.DijkstraAlgorithm.DijkstraContinuableRequest;
import jsettlers.logic.buildings.Building;
import jsettlers.logic.constants.Constants;
import jsettlers.logic.newmovable.NewMovable;
import jsettlers.logic.newmovable.interfaces.IAttackable;
import jsettlers.logic.newmovable.interfaces.IAttackableMovable;
import jsettlers.logic.objects.StandardMapObject;
import random.RandomSingleton;

/**
 * Tower building.
 * 
 * <p>
 * 
 * @author Andreas Eberle
 * 
 */
public class OccupyingBuilding extends Building implements IBuilding.IOccupyed, IPathCalculateable, IOccupyableBuilding, Serializable {
	private static final long serialVersionUID = 5267249978497095473L;

	private static LinkedList<OccupyingBuilding> allOccupyingBuildings = new LinkedList<OccupyingBuilding>();

	private final LinkedList<TowerOccupyer> occupiers;
	private final LinkedList<ESearchType> searchedSoldiers = new LinkedList<ESearchType>();
	private final LinkedList<OccupyerPlace> emptyPlaces = new LinkedList<OccupyerPlace>();

	private DijkstraContinuableRequest request;
	private byte delayCtr;

	private boolean occupiedArea;
	private float doorHealth = 1.0f;
	private boolean inFight;
	private AttackableTowerMapObject attackableTowerObject = null;

	private final int[] maximumRequestedSoldiers = new int[ESoldierType.values().length];
	private final int[] currentlyCommingSoldiers = new int[ESoldierType.values().length];

	public OccupyingBuilding(EBuildingType type, byte player) {
		super(type, player);

		this.occupiers = new LinkedList<TowerOccupyer>(); // for testing purposes

		initSoldierRequests();

		allOccupyingBuildings.add(this);

		delayCtr = (byte) RandomSingleton.getInt(0, 3);
	}

	private synchronized void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ois.defaultReadObject();
		allOccupyingBuildings.add(this);
	}

	private void initSoldierRequests() {
		final OccupyerPlace[] occupyerPlaces = super.getBuildingType().getOccupyerPlaces();
		if (occupyerPlaces.length > 0) {
			for (OccupyerPlace currPlace : occupyerPlaces) {
				emptyPlaces.add(currPlace);
				searchedSoldiers.add(currPlace.getType() == ESoldierType.INFANTRY ? ESearchType.SOLDIER_SWORDSMAN : ESearchType.SOLDIER_BOWMAN);
			}
		}
	}

	@Override
	protected final void constructionFinishedEvent() {
		setAttackableTowerObject(true);
	}

	private void setAttackableTowerObject(boolean set) {
		if (set) {
			attackableTowerObject = new AttackableTowerMapObject();
			super.getGrid().getMapObjectsManager().addAttackableTowerObject(getDoor(), attackableTowerObject);
		} else {
			super.getGrid().getMapObjectsManager().removeMapObjectType(getDoor().getX(), getDoor().getY(), EMapObjectType.ATTACKABLE_TOWER);
			attackableTowerObject = null;
		}
	}

	public void changePlayerTo(byte player) {
		assert occupiers.isEmpty() : "there cannot be any occupies in the tower when changing the player.";

		setAttackableTowerObject(false);
		super.placeFlag(false);

		request = null;
		searchedSoldiers.clear();
		emptyPlaces.clear();

		super.setPlayer(player); // set the new player.

		if (occupiedArea) { // free the area if it had been occupied.
			freeArea();
			occupiedArea = false;
		}

		doorHealth = 0.1f;

		super.placeFlag(true);
		setAttackableTowerObject(true);

		initSoldierRequests();
	}

	@Override
	protected final void appearedEvent() {
		occupyArea();
		searchedSoldiers.remove(ESearchType.SOLDIER_SWORDSMAN);
	}

	public final MapCircle getOccupyablePositions() {
		return new MapCircle(super.getPos(), CommonConstants.TOWERRADIUS);
	}

	@Override
	protected final EMapObjectType getFlagType() {
		return EMapObjectType.FLAG_DOOR;
	}

	@Override
	public final void stopOrStartWorking(boolean stop) {
	}

	@Override
	protected final void subTimerEvent() {
		delayCtr++;
		if (delayCtr > 5) {
			delayCtr = 0;

			if (doorHealth < 1 && !inFight) {
				doorHealth = Math.min(1, doorHealth + Constants.TOWER_DOOR_REGENERATION);
			}

			if (!searchedSoldiers.isEmpty()) {
				if (request == null) {
					request = new DijkstraContinuableRequest(this, super.getPos().getX(), super.getPos().getY(), (short) 1,
							Constants.TOWER_SEARCH_RADIUS);
				}
				request.setSearchType(searchedSoldiers.peek());

				Path path = super.getGrid().getDijkstra().find(request);
				if (path != null) {
					System.out.println("soldier found");

					NewMovable soldier = super.getGrid().getMovable(path.getTargetPos());
					if (soldier != null && soldier.setOccupyableBuilding(this)) {
						ESearchType searchedSoldier = searchedSoldiers.pop();
						currentlyCommingSoldiers[getSoldierType(searchedSoldier).ordinal()]++;
					}// soldier wasn't at the position
				}
			}
		}
	}

	private ESoldierType getSoldierType(ESearchType searchedSoldier) {
		switch (searchedSoldier) {
		case SOLDIER_BOWMAN:
			return ESoldierType.BOWMAN;
		case SOLDIER_PIKEMAN:
		case SOLDIER_SWORDSMAN:
			return ESoldierType.INFANTRY;
		default:
			throw new IllegalArgumentException(searchedSoldier + " is no soldier search type!");
		}
	}

	@Override
	protected void positionedEvent(ShortPoint2D pos) {
	}

	@Override
	protected final void killedEvent() {
		setSelected(false);

		if (occupiedArea) {
			freeArea();

			int idx = 0;
			FreeMapArea buildingArea = super.getBuildingArea();
			for (TowerOccupyer curr : occupiers) {
				addInformableMapObject(curr, false);// if curr is a bowman, this removes the informable map object.

				curr.getSoldier().leaveOccupyableBuilding(buildingArea.get(idx));
				idx++;
			}

			occupiers.clear();
		}

		allOccupyingBuildings.remove(this);

		setAttackableTowerObject(false);
	}

	private void freeArea() {
		MapCircle occupied = getOccupyablePositions();
		super.getGrid().freeOccupiedArea(occupied, super.getPos());
	}

	@Override
	public final List<? extends IBuildingOccupyer> getOccupyers() {
		return occupiers;
	}

	@Override
	public final boolean needsPlayersGround() { // soldiers don't need players ground.
		return false;
	}

	@Override
	public final OccupyerPlace addSoldier(IBuildingOccupyableMovable soldier) {
		OccupyerPlace freePosition = findFreePositionFor(soldier.getSoldierType());

		emptyPlaces.remove(freePosition);
		currentlyCommingSoldiers[freePosition.getType().ordinal()]--;

		TowerOccupyer towerOccupier = new TowerOccupyer(freePosition, soldier);
		occupiers.add(towerOccupier);

		occupyArea();

		soldier.setSelected(super.isSelected());

		addInformableMapObject(towerOccupier, true);

		return freePosition;
	}

	protected TowerOccupyer removeSoldier() {
		TowerOccupyer removedSoldier = occupiers.removeFirst();

		addInformableMapObject(removedSoldier, false);

		return removedSoldier;
	}

	/**
	 * Adds or removes the informable map object for the given soldier.
	 * 
	 * @param soldier
	 * @param add
	 *            if true, the object is added<br>
	 *            if false, the object is removed.
	 */
	private void addInformableMapObject(TowerOccupyer soldier, boolean add) {
		if (soldier.place.getType() == ESoldierType.BOWMAN) {
			ShortPoint2D position = getTowerBowmanSearchPosition(soldier.place);

			if (add) {
				super.getGrid().getMapObjectsManager().addInformableMapObjectAt(position, soldier.getSoldier().getMovable());
			} else {
				super.getGrid().getMapObjectsManager().removeMapObjectType(position.getX(), position.getY(), EMapObjectType.INFORMABLE_MAP_OBJECT);
			}
		}
	}

	@Override
	public ShortPoint2D getTowerBowmanSearchPosition(OccupyerPlace place) {
		ShortPoint2D pos = place.getPosition().calculatePoint(super.getPos());
		// FIXME @Andreas Eberle introduce new field in the buildings xml file
		ShortPoint2D position = new ShortPoint2D(pos.getX() + 3, pos.getY() + 6);
		return position;
	}

	private OccupyerPlace findFreePositionFor(ESoldierType soldierType) {
		OccupyerPlace freePosition = null;
		for (OccupyerPlace curr : emptyPlaces) {
			if (curr.getType() == soldierType) {
				freePosition = curr;

				break;
			}
		}
		return freePosition;
	}

	private final void occupyArea() {
		if (!occupiedArea) {
			MapCircle occupying = getOccupyablePositions();
			super.getGrid().occupyArea(occupying, new FreeMapArea(super.getPos(), super.getBuildingType().getProtectedTiles()), super.getPlayer());
			occupiedArea = true;
		}
	}

	@Override
	public final void requestFailed(EMovableType soldierType) {
		ESearchType searchType = getSearchType(soldierType);

		currentlyCommingSoldiers[getSoldierType(searchType).ordinal()]--;

		if (searchType != null)
			searchedSoldiers.add(searchType);
	}

	@Override
	public final ShortPoint2D getPosition(IBuildingOccupyableMovable soldier) {
		for (TowerOccupyer curr : occupiers) {
			if (curr.getSoldier() == soldier) {
				return curr.place.getPosition().calculatePoint(super.getPos());
			}
		}
		return null;
	}

	@Override
	public final void setSelected(boolean selected) {
		super.setSelected(selected);
		for (TowerOccupyer curr : occupiers) {
			curr.getSoldier().setSelected(selected);
		}

		if (attackableTowerObject != null && attackableTowerObject.currDefender != null) {
			attackableTowerObject.currDefender.getSoldier().setSelected(selected);
		}
	}

	private final ESearchType getSearchType(EMovableType movableType) {
		ESearchType searchType;

		switch (movableType) {
		case BOWMAN_L1:
		case BOWMAN_L2:
		case BOWMAN_L3:
			searchType = ESearchType.SOLDIER_BOWMAN;
			break;
		case SWORDSMAN_L1:
		case SWORDSMAN_L2:
		case SWORDSMAN_L3:
			searchType = ESearchType.SOLDIER_SWORDSMAN;
			break;
		case PIKEMAN_L1:
		case PIKEMAN_L2:
		case PIKEMAN_L3:
			searchType = ESearchType.SOLDIER_PIKEMAN;
			break;
		default:
			return null;
		}
		return searchType;
	}

	@Override
	public final boolean isOccupied() {
		return occupiers.isEmpty();
	}

	@Override
	public void towerDefended(IBuildingOccupyableMovable soldier) {
		inFight = false;
		occupiers.add(new TowerOccupyer(attackableTowerObject.currDefender.place, soldier));
		attackableTowerObject.currDefender = null;
	}

	public final static LinkedList<OccupyingBuilding> getAllOccupyingBuildings() {
		return allOccupyingBuildings;
	}

	public static void dropAllBuildings() {
		allOccupyingBuildings.clear();
	}

	@Override
	public int getMaximumRequestedSoldiers(ESoldierType type) {
		return maximumRequestedSoldiers[type.ordinal()];
	}

	@Override
	public void setMaximumRequestedSoldiers(ESoldierType type, int max) {
		final OccupyerPlace[] occupyerPlaces = super.getBuildingType().getOccupyerPlaces();
		int physicalMax = 0;
		for (OccupyerPlace place : occupyerPlaces) {
			if (place.getType() == type) {
				physicalMax++;
			}
		}
		if (max > physicalMax) {
			maximumRequestedSoldiers[type.ordinal()] = physicalMax;
		} else if (max <= 0) {
			maximumRequestedSoldiers[type.ordinal()] = 0;
			/* There must always be someone in a tower, or at least requested. */
			/*
			 * NOTE: We might skip this, and instead let the last soldier not leave the tower if the tower would become empty afterwards
			 */
			boolean isEmpty = true;
			for (int count : maximumRequestedSoldiers) {
				isEmpty &= count == 0;
			}
			if (isEmpty && physicalMax >= 1) {
				maximumRequestedSoldiers[type.ordinal()] = 1;
			}
		} else {
			maximumRequestedSoldiers[type.ordinal()] = max;
		}
	}

	@Override
	public int getCurrentlyCommingSoldiers(ESoldierType type) {
		return currentlyCommingSoldiers[type.ordinal()];
	}

	private final static class TowerOccupyer implements IBuildingOccupyer, Serializable {
		private static final long serialVersionUID = -1491427078923346232L;

		private final OccupyerPlace place;
		private final IBuildingOccupyableMovable soldier;

		TowerOccupyer(OccupyerPlace place, IBuildingOccupyableMovable soldier) {
			this.place = place;
			this.soldier = soldier;
		}

		@Override
		public OccupyerPlace getPlace() {
			return place;
		}

		@Override
		public IMovable getMovable() {
			return soldier.getMovable();
		}

		public IBuildingOccupyableMovable getSoldier() {
			return soldier;
		}

	}

	/**
	 * This map object lies at the door of a tower and is used to signal soldiers that there is something to attack.
	 * 
	 * @author Andreas Eberle
	 * 
	 */
	public class AttackableTowerMapObject extends StandardMapObject implements IAttackable, IAttackableTowerMapObject {
		private static final long serialVersionUID = -5137593316096740750L;
		private TowerOccupyer currDefender;

		public AttackableTowerMapObject() {
			super(EMapObjectType.ATTACKABLE_TOWER, false, OccupyingBuilding.this.getPlayer());
		}

		@Override
		public ShortPoint2D getPos() {
			return OccupyingBuilding.this.getDoor();
		}

		@Override
		public void receiveHit(float strength, byte player) {
			if (doorHealth > 0) {
				doorHealth -= strength / Constants.DOOR_HIT_RESISTENCY_FACTOR;

				if (doorHealth <= 0) {
					doorHealth = 0;
					inFight = true;

					OccupyingBuilding.this.getGrid().getMapObjectsManager()
							.addSelfDeletingMapObject(getPos(), EMapObjectType.GHOST, Constants.GHOST_PLAY_DURATION, getPlayer());

					pollNewDefender();
				}
			} else if (currDefender != null) {
				IAttackableMovable movable = currDefender.getSoldier().getMovable();
				movable.receiveHit(strength, player);

				if (movable.getHealth() <= 0) {
					if (occupiers.isEmpty()) {
						currDefender = null;
						changePlayerTo(player);
					} else {
						emptyPlaces.add(currDefender.place); // request a new soldier.
						searchedSoldiers.add(getSearchType(currDefender.getSoldier().getMovableType()));

						pollNewDefender();
					}
				}
			}
		}

		private void pollNewDefender() {
			if (occupiers.isEmpty()) {
				currDefender = null;
			} else {
				currDefender = removeSoldier();
				currDefender.getSoldier().setDefendingAt(getPos());
			}
		}

		@Override
		public float getHealth() {
			if (doorHealth > 0) {
				return doorHealth;
			} else {
				return currDefender == null ? 0 : currDefender.getMovable().getHealth();
			}
		}

		@Override
		public boolean isAttackable() {
			return true;
		}

		@Override
		public IMovable getMovable() {
			return currDefender == null ? null : currDefender.getSoldier().getMovable();
		}

		@Override
		public EMovableType getMovableType() {
			assert false : "This should never have been called";
			return EMovableType.SWORDSMAN_L1;
		}

		@Override
		public void informAboutAttackable(IAttackable attackable) {
		}
	}

}
