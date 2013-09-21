package jsettlers.logic.map.newGrid;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

import jsettlers.common.Color;
import jsettlers.common.CommonConstants;
import jsettlers.common.buildings.EBuildingType;
import jsettlers.common.buildings.IBuilding;
import jsettlers.common.landscape.ELandscapeType;
import jsettlers.common.landscape.EResourceType;
import jsettlers.common.map.IGraphicsBackgroundListener;
import jsettlers.common.map.IGraphicsGrid;
import jsettlers.common.map.IMapData;
import jsettlers.common.map.object.BuildingObject;
import jsettlers.common.map.object.MapObject;
import jsettlers.common.map.object.MapStoneObject;
import jsettlers.common.map.object.MapTreeObject;
import jsettlers.common.map.object.MovableObject;
import jsettlers.common.map.object.StackObject;
import jsettlers.common.map.partition.IPartitionSettings;
import jsettlers.common.map.shapes.FreeMapArea;
import jsettlers.common.map.shapes.HexGridArea;
import jsettlers.common.map.shapes.HexGridArea.HexGridAreaIterator;
import jsettlers.common.map.shapes.MapCircle;
import jsettlers.common.map.shapes.MapNeighboursArea;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.mapobject.IMapObject;
import jsettlers.common.material.EMaterialType;
import jsettlers.common.material.ESearchType;
import jsettlers.common.movable.EDirection;
import jsettlers.common.movable.EMovableType;
import jsettlers.common.movable.IMovable;
import jsettlers.common.position.RelativePoint;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.input.IGuiInputGrid;
import jsettlers.input.UIState;
import jsettlers.logic.algorithms.borders.BordersThread;
import jsettlers.logic.algorithms.borders.IBordersThreadGrid;
import jsettlers.logic.algorithms.construction.AbstractConstructionMarkableMap;
import jsettlers.logic.algorithms.fogofwar.IFogOfWarGrid;
import jsettlers.logic.algorithms.fogofwar.IViewDistancable;
import jsettlers.logic.algorithms.fogofwar.NewFogOfWar;
import jsettlers.logic.algorithms.landmarks.EnclosedBlockedAreaFinderAlgorithm;
import jsettlers.logic.algorithms.landmarks.IEnclosedBlockedAreaFinderGrid;
import jsettlers.logic.algorithms.path.IPathCalculateable;
import jsettlers.logic.algorithms.path.Path;
import jsettlers.logic.algorithms.path.area.IInAreaFinderMap;
import jsettlers.logic.algorithms.path.area.InAreaFinder;
import jsettlers.logic.algorithms.path.astar.AbstractAStar;
import jsettlers.logic.algorithms.path.astar.BucketQueueAStar;
import jsettlers.logic.algorithms.path.astar.normal.IAStarPathMap;
import jsettlers.logic.algorithms.path.dijkstra.DijkstraAlgorithm;
import jsettlers.logic.algorithms.path.dijkstra.IDijkstraPathMap;
import jsettlers.logic.algorithms.previewimage.PreviewImageCreator;
import jsettlers.logic.buildings.Building;
import jsettlers.logic.buildings.IBuildingsGrid;
import jsettlers.logic.buildings.military.IOccupyableBuilding;
import jsettlers.logic.buildings.workers.WorkerBuilding;
import jsettlers.logic.constants.Constants;
import jsettlers.logic.constants.MatchConstants;
import jsettlers.logic.map.newGrid.flags.FlagsGrid;
import jsettlers.logic.map.newGrid.landscape.LandscapeGrid;
import jsettlers.logic.map.newGrid.movable.MovableGrid;
import jsettlers.logic.map.newGrid.objects.AbstractHexMapObject;
import jsettlers.logic.map.newGrid.objects.IMapObjectsManagerGrid;
import jsettlers.logic.map.newGrid.objects.MapObjectsManager;
import jsettlers.logic.map.newGrid.objects.ObjectsGrid;
import jsettlers.logic.map.newGrid.partition.IPlayerChangedListener;
import jsettlers.logic.map.newGrid.partition.PartitionsGrid;
import jsettlers.logic.map.newGrid.partition.manager.PartitionManager;
import jsettlers.logic.map.newGrid.partition.manager.manageables.IManageableBearer;
import jsettlers.logic.map.newGrid.partition.manager.manageables.IManageableBricklayer;
import jsettlers.logic.map.newGrid.partition.manager.manageables.IManageableDigger;
import jsettlers.logic.map.newGrid.partition.manager.manageables.IManageableWorker;
import jsettlers.logic.map.newGrid.partition.manager.manageables.interfaces.IBarrack;
import jsettlers.logic.map.newGrid.partition.manager.manageables.interfaces.IDiggerRequester;
import jsettlers.logic.map.newGrid.partition.manager.materials.requests.MaterialRequestObject;
import jsettlers.logic.map.save.MapFileHeader;
import jsettlers.logic.map.save.MapFileHeader.MapType;
import jsettlers.logic.map.save.MapList;
import jsettlers.logic.newmovable.NewMovable;
import jsettlers.logic.newmovable.interfaces.AbstractNewMovableGrid;
import jsettlers.logic.newmovable.interfaces.IAttackable;
import jsettlers.logic.objects.arrow.ArrowObject;
import jsettlers.logic.player.Player;
import jsettlers.logic.stack.IRequestsStackGrid;

/**
 * This is the main grid offering an interface for interacting with the grid.
 * 
 * @author Andreas Eberle
 */
public final class MainGrid implements Serializable {
	private static final long serialVersionUID = 3824511313693431423L;

	final String mapId;
	final String mapName;

	final short width;
	final short height;

	final LandscapeGrid landscapeGrid;
	final ObjectsGrid objectsGrid;
	final PartitionsGrid partitionsGrid;
	final MovableGrid movableGrid;
	final FlagsGrid flagsGrid;

	final MovablePathfinderGrid movablePathfinderGrid;
	final MapObjectsManager mapObjectsManager;
	final BuildingsGrid buildingsGrid;
	final NewFogOfWar fogOfWar;

	transient IGraphicsGrid graphicsGrid;
	transient ConstructionMarksGrid constructionMarksGrid;
	transient BordersThread bordersThread;
	transient IGuiInputGrid guiInputGrid;
	private transient IEnclosedBlockedAreaFinderGrid enclosedBlockedAreaFinderGrid;

	public MainGrid(String mapId, String mapName, short width, short height, byte numberOfPlayers, byte fowPlayer) {
		this.mapId = mapId;
		this.mapName = mapName;

		this.width = width;
		this.height = height;

		this.flagsGrid = new FlagsGrid(width, height);
		this.partitionsGrid = new PartitionsGrid(width, height, numberOfPlayers, flagsGrid);
		this.movablePathfinderGrid = new MovablePathfinderGrid();
		this.mapObjectsManager = new MapObjectsManager(new MapObjectsManagerGrid());

		this.objectsGrid = new ObjectsGrid(width, height);
		this.landscapeGrid = new LandscapeGrid(width, height);
		this.movableGrid = new MovableGrid(width, height, landscapeGrid);

		this.buildingsGrid = new BuildingsGrid();
		this.fogOfWar = new NewFogOfWar(width, height, fowPlayer, false);

		initAdditional();
	}

	private void initAdditional() {
		this.graphicsGrid = new GraphicsGrid();
		this.constructionMarksGrid = new ConstructionMarksGrid();
		this.bordersThread = new BordersThread(new BordersThreadGrid());
		this.guiInputGrid = new GUIInputGrid();

		this.partitionsGrid.setPlayerChangedListener(new PlayerChangedListener());
		this.enclosedBlockedAreaFinderGrid = new EnclosedBlockedAreaFinderGrid();
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ois.defaultReadObject();
		initAdditional();
	}

	public void startThreads() {
		bordersThread.start();
		fogOfWar.start(new FogOfWarGrid());
		partitionsGrid.startThreads();
	}

	public void stopThreads() {
		bordersThread.cancel();
		fogOfWar.cancel();
		partitionsGrid.cancelThreads();
	}

	public void waitForThreadsToFinish() {
		try {
			Thread.sleep(300); // TODO @Andreas Eberle: Wait for the threads to finish their work
		} catch (InterruptedException e) {
		}
	}

	public MainGrid(String mapId, String mapName, IMapData mapGrid, byte players, byte fowPlayer) {
		this(mapId, mapName, (short) mapGrid.getWidth(), (short) mapGrid.getHeight(), players, fowPlayer);

		for (short y = 0; y < height; y++) {
			for (short x = 0; x < width; x++) {
				ELandscapeType landscape = mapGrid.getLandscape(x, y);
				setLandscapeTypeAt(x, y, landscape);
				landscapeGrid.setHeightAt(x, y, mapGrid.getLandscapeHeight(x, y));
				landscapeGrid.setResourceAt(x, y, mapGrid.getResourceType(x, y), mapGrid.getResourceAmount(x, y));
				landscapeGrid.setBlockedPartition(x, y, mapGrid.getBlockedPartition(x, y));
			}
		}

		// two phases, we might need the base grid tiles to add blocking, status
		for (short y = 0; y < height; y++) {
			for (short x = 0; x < width; x++) {
				MapObject object = mapGrid.getMapObject(x, y);
				if (object != null && isOccupyableBuilding(object)) {
					addMapObject(x, y, object);
				}
				if ((x + y / 2) % 4 == 0 && y % 4 == 0 && isInsideWater(x, y)) {
					mapObjectsManager.addWaves(x, y);
					if (landscapeGrid.getResourceAmountAt(x, y) > 50) {
						mapObjectsManager.addFish(x, y);
					}
				}
			}
		}

		for (short y = 0; y < height; y++) {
			for (short x = 0; x < width; x++) {
				MapObject object = mapGrid.getMapObject(x, y);
				if (object != null && !isOccupyableBuilding(object)) {
					addMapObject(x, y, object);
				}
			}
		}
		System.out.println("grid filled");
	}

	private static boolean isOccupyableBuilding(MapObject object) {
		return object instanceof BuildingObject && ((BuildingObject) object).getType().getOccupyerPlaces().length > 0;
	}

	private boolean isInsideWater(short x, short y) {
		return isWaterSafe(x - 1, y) && isWaterSafe(x, y) && isWaterSafe(x + 1, y) && isWaterSafe(x - 1, y + 1) && isWaterSafe(x, y + 1)
				&& isWaterSafe(x + 1, y + 1) && isWaterSafe(x, y + 2) && isWaterSafe(x + 1, y + 2) && isWaterSafe(x + 2, y + 2);
	}

	private boolean isWaterSafe(int x, int y) {
		return isInBounds((short) x, (short) y) && landscapeGrid.getLandscapeTypeAt((short) x, (short) y).isWater();
	}

	private void addMapObject(short x, short y, MapObject object) {
		ShortPoint2D pos = new ShortPoint2D(x, y);

		if (object instanceof MapTreeObject) {
			if (isInBounds(x, y) && movablePathfinderGrid.pathfinderGrid.isTreePlantable(x, y)) {
				mapObjectsManager.plantAdultTree(pos);
			}
		} else if (object instanceof MapStoneObject) {
			mapObjectsManager.addStone(pos, ((MapStoneObject) object).getCapacity());
		} else if (object instanceof StackObject) {
			placeStack(pos, ((StackObject) object).getType(), ((StackObject) object).getCount());
		} else if (object instanceof BuildingObject) {
			BuildingObject buildingObject = (BuildingObject) object;
			Building building = constructBuildingAt(pos, buildingObject.getType(), partitionsGrid.getPlayer(buildingObject.getPlayerId()), true);

			if (building instanceof IOccupyableBuilding) {
				NewMovable soldier = createNewMovableAt(building.getDoor(), EMovableType.SWORDSMAN_L1, building.getPlayer());
				soldier.setOccupyableBuilding((IOccupyableBuilding) building);
			}
		} else if (object instanceof MovableObject) {
			MovableObject movableObject = (MovableObject) object;
			createNewMovableAt(pos, movableObject.getType(), partitionsGrid.getPlayer(movableObject.getPlayerId()));
		}
	}

	public MapFileHeader generateSaveHeader() {
		// TODO: description
		// TODO: count alive players, count all players
		PreviewImageCreator previewImageCreator = new PreviewImageCreator(width, height, MapFileHeader.PREVIEW_IMAGE_SIZE,
				landscapeGrid.getPreviewImageDataSupplier());

		short[] bgImage = previewImageCreator.getPreviewImage();

		return new MapFileHeader(MapType.SAVED_SINGLE, mapName, mapId, "TODO: description", width, height, (short) 1, (short) 1, new Date(), bgImage);
	}

	private void placeStack(ShortPoint2D pos, EMaterialType materialType, int count) {
		for (int i = 0; i < count; i++) {
			movablePathfinderGrid.dropMaterial(pos, materialType, true);
		}
	}

	public IGraphicsGrid getGraphicsGrid() {
		return graphicsGrid;
	}

	public IGuiInputGrid getGuiInputGrid() {
		return guiInputGrid;
	}

	/**
	 * FOR TESTS ONLY!!
	 * 
	 * @return
	 */
	public IAStarPathMap getPathfinderGrid() {
		return movablePathfinderGrid.pathfinderGrid;
	}

	public final boolean isInBounds(int x, int y) {
		return x >= 0 && x < width && y >= 0 && y < height;
	}

	public final NewMovable createNewMovableAt(ShortPoint2D pos, EMovableType type, Player player) {
		return new NewMovable(movablePathfinderGrid, type, pos, player);
	}

	/**
	 * Creates a new building at the given position.
	 * 
	 * @param position
	 *            The position to place the building.
	 * @param type
	 *            The {@link EBuildingType} of the building.
	 * @param player
	 *            The player owning the building.
	 * @param fullyConstructed
	 *            If true, the building will be placed as fully constructed building.<br>
	 *            If false, it will only be placed as a construction site.
	 * @return The newly created building.
	 */
	final Building constructBuildingAt(ShortPoint2D position, EBuildingType type, Player player, boolean fullyConstructed) {
		Building building = Building.getBuilding(type, player);
		building.constructAt(buildingsGrid, position, fullyConstructed);

		if (fullyConstructed) {
			byte buildingHeight = landscapeGrid.getHeightAt(position.x, position.y);
			for (RelativePoint curr : building.getFlattenTiles()) {
				landscapeGrid.flattenAndChangeHeightTowards(curr.getDx() + position.x, curr.getDy() + position.y, buildingHeight);
			}
		}

		return building;
	}

	protected final void setLandscapeTypeAt(int x, int y, ELandscapeType newType) {
		if (newType.isBlocking) {
			flagsGrid.setBlockedAndProtected(x, y, true);
		} else {
			if (landscapeGrid.getLandscapeTypeAt(x, y).isBlocking) {
				flagsGrid.setBlockedAndProtected(x, y, false);
			}
		}
		landscapeGrid.setLandscapeTypeAt(x, y, newType);
	}

	final void checkPositionThatChangedPlayer(ShortPoint2D position) {
		if (!isInBounds(position.x, position.y)) {
			return;
		}

		EnclosedBlockedAreaFinderAlgorithm.checkLandmark(enclosedBlockedAreaFinderGrid, flagsGrid.getBlockedContainingProvider(), position);

		NewMovable movable = movableGrid.getMovableAt(position.x, position.y);
		if (movable != null) {
			movable.checkPlayerOfPosition(partitionsGrid.getPlayerAt(position.x, position.y));
		}
	}

	final class PathfinderGrid implements IAStarPathMap, IDijkstraPathMap, IInAreaFinderMap, Serializable {
		private static final long serialVersionUID = -2775530442375843213L;

		@Override
		public boolean isBlocked(IPathCalculateable requester, int x, int y) {
			return flagsGrid.isBlocked(x, y) || (requester.needsPlayersGround() && requester.getPlayerId() != partitionsGrid.getPlayerIdAt(x, y));
		}

		@Override
		public final float getCost(int sx, int sy, int tx, int ty) {
			// return Constants.TILE_PATHFINDER_COST * (flagsGrid.isProtected(sx, sy) ? 3.5f : 1);
			return 1;
		}

		@Override
		public final void markAsOpen(int x, int y) {
			landscapeGrid.setDebugColor(x, y, Color.BLUE.getARGB());
		}

		@Override
		public final void markAsClosed(int x, int y) {
			landscapeGrid.setDebugColor(x, y, Color.RED.getARGB());
		}

		@Override
		public final void setDijkstraSearched(int x, int y) {
			markAsOpen(x, y);
		}

		@Override
		public final boolean fitsSearchType(int x, int y, ESearchType searchType, IPathCalculateable pathCalculable) {
			switch (searchType) {

			case UNENFORCED_FOREIGN_GROUND:
				return !flagsGrid.isBlocked(x, y) && !hasSamePlayer(x, y, pathCalculable) && !partitionsGrid.isEnforcedByTower(x, y);

			case OWN_GROUND:
				return !flagsGrid.isBlocked(x, y) && hasSamePlayer(x, y, pathCalculable);

			case CUTTABLE_TREE:
				return isInBounds((short) (x - 1), (short) (y - 1))
						&& objectsGrid.hasCuttableObject((short) (x - 1), (short) (y - 1), EMapObjectType.TREE_ADULT)
						&& hasSamePlayer((short) (x - 1), (short) (y - 1), pathCalculable) && !isMarked(x, y);

			case PLANTABLE_TREE:
				return y < height - 1 && isTreePlantable(x, y + 1) && !hasProtectedNeighbor(x, y + 1)
						&& hasSamePlayer(x, (short) (y + 1), pathCalculable) && !isMarked(x, y);

			case PLANTABLE_CORN:
				return !isMarked(x, y) && !flagsGrid.isProtected(x, y) && hasSamePlayer(x, y, pathCalculable) && isCornPlantable(x, y);

			case CUTTABLE_CORN:
				return isCornCuttable(x, y) && hasSamePlayer(x, y, pathCalculable) && !isMarked(x, y);

			case CUTTABLE_STONE:
				return y + 1 < height && x - 1 < width && objectsGrid.hasCuttableObject((short) (x - 1), (short) (y + 1), EMapObjectType.STONE)
						&& hasSamePlayer(x, y, pathCalculable) && !isMarked(x, y);

			case ENEMY: {
				IMovable movable = movableGrid.getMovableAt(x, y);
				return movable != null && movable.getPlayerId() != pathCalculable.getPlayerId();
			}

			case RIVER:
				return isRiver(x, y) && hasSamePlayer(x, y, pathCalculable) && !isMarked(x, y);

			case FISHABLE:
				return hasSamePlayer(x, y, pathCalculable) && hasNeighbourLandscape(x, y, ELandscapeType.WATER1);

			case NON_BLOCKED_OR_PROTECTED:
				return !(flagsGrid.isProtected(x, y) || flagsGrid.isBlocked(x, y))
						&& (!pathCalculable.needsPlayersGround() || hasSamePlayer(x, y, pathCalculable)) && movableGrid.getMovableAt(x, y) == null;

			case SOLDIER_BOWMAN:
				return isSoldierAt(x, y, searchType, pathCalculable.getPlayerId());
			case SOLDIER_SWORDSMAN:
				return isSoldierAt(x, y, searchType, pathCalculable.getPlayerId());
			case SOLDIER_PIKEMAN:
				return isSoldierAt(x, y, searchType, pathCalculable.getPlayerId());

			case RESOURCE_SIGNABLE:
				return isInBounds(x, y) && !flagsGrid.isProtected(x, y) && !flagsGrid.isMarked(x, y) && canAddRessourceSign(x, y);

			case FOREIGN_MATERIAL:
				return isInBounds(x, y) && !hasSamePlayer(x, y, pathCalculable) && mapObjectsManager.hasStealableMaterial(x, y);

			default:
				System.err.println("can't handle search type in fitsSearchType(): " + searchType);
				return false;
			}
		}

		protected final boolean canAddRessourceSign(int x, int y) {
			return x % 2 == 0
					&& y % 2 == 0
					&& landscapeGrid.getLandscapeTypeAt(x, y) == ELandscapeType.MOUNTAIN
					&& !(objectsGrid.hasMapObjectType(x, y, EMapObjectType.FOUND_COAL)
							|| objectsGrid.hasMapObjectType(x, y, EMapObjectType.FOUND_IRON) || objectsGrid.hasMapObjectType(x, y,
							EMapObjectType.FOUND_GOLD));
		}

		private final boolean isSoldierAt(int x, int y, ESearchType searchType, byte player) {
			NewMovable movable = movableGrid.getMovableAt(x, y);
			if (movable == null) {
				return false;
			} else {
				if (movable.getPlayerId() == player && movable.canOccupyBuilding()) {
					EMovableType type = movable.getMovableType();
					switch (searchType) {
					case SOLDIER_BOWMAN:
						return type == EMovableType.BOWMAN_L1 || type == EMovableType.BOWMAN_L2 || type == EMovableType.BOWMAN_L3;
					case SOLDIER_SWORDSMAN:
						return type == EMovableType.SWORDSMAN_L1 || type == EMovableType.SWORDSMAN_L2 || type == EMovableType.SWORDSMAN_L3;
					case SOLDIER_PIKEMAN:
						return type == EMovableType.PIKEMAN_L1 || type == EMovableType.PIKEMAN_L2 || type == EMovableType.PIKEMAN_L3;
					default:
						return false;
					}
				} else {
					return false;
				}
			}
		}

		private final boolean isMarked(int x, int y) {
			return flagsGrid.isMarked(x, y);
		}

		private final boolean hasProtectedNeighbor(int x, int y) {
			for (EDirection currDir : EDirection.values) {
				if (flagsGrid.isProtected(currDir.getNextTileX(x), currDir.getNextTileY(y)))
					return true;
			}
			return false;
		}

		private final boolean hasNeighbourLandscape(int x, int y, ELandscapeType landscape) {
			for (ShortPoint2D pos : new MapNeighboursArea(new ShortPoint2D(x, y))) {
				short currX = pos.x;
				short currY = pos.y;
				if (isInBounds(currX, currY) && landscapeGrid.getLandscapeTypeAt(currX, currY) == landscape) {
					return true;
				}
			}
			return false;
		}

		private final boolean hasSamePlayer(int x, int y, IPathCalculateable requester) {
			return partitionsGrid.getPlayerIdAt(x, y) == requester.getPlayerId();
		}

		private final boolean isRiver(int x, int y) {
			ELandscapeType type = landscapeGrid.getLandscapeTypeAt(x, y);
			return type == ELandscapeType.RIVER1 || type == ELandscapeType.RIVER2 || type == ELandscapeType.RIVER3 || type == ELandscapeType.RIVER4;
		}

		final boolean isTreePlantable(int x, int y) {
			return landscapeGrid.getLandscapeTypeAt(x, y).isGrass() && !flagsGrid.isProtected(x, y) && !hasBlockedNeighbor((short) x, (short) y);
		}

		private final boolean hasBlockedNeighbor(short x, short y) {
			for (EDirection currDir : EDirection.values) {
				short currX = currDir.getNextTileX(x);
				short currY = currDir.getNextTileY(y);
				if (!isInBounds(currX, currY) || flagsGrid.isBlocked(currX, currY)) {
					return true;
				}
			}

			return false;
		}

		private final boolean isCornPlantable(int x, int y) {
			ELandscapeType landscapeType = landscapeGrid.getLandscapeTypeAt(x, y);
			return (landscapeType == ELandscapeType.GRASS || landscapeType == ELandscapeType.EARTH) && !flagsGrid.isProtected(x, y)
					&& !hasProtectedNeighbor(x, y)
					&& !objectsGrid.hasMapObjectType(x, y, EMapObjectType.CORN_GROWING)
					&& !objectsGrid.hasMapObjectType(x, y, EMapObjectType.CORN_ADULT)
					&& !objectsGrid.hasNeighborObjectType(x, y, EMapObjectType.CORN_ADULT)
					&& !objectsGrid.hasNeighborObjectType(x, y, EMapObjectType.CORN_GROWING)
					&& areAllNeighborsOneOf(x, y, 2, ELandscapeType.GRASS, ELandscapeType.EARTH);
		}

		private boolean areAllNeighborsOneOf(int x, int y, int radius, ELandscapeType... types) {
			for (ShortPoint2D currPos : new HexGridArea(x, y, 1, radius)) {
				boolean found = false;

				ELandscapeType neighborType = landscapeGrid.getLandscapeTypeAt(currPos.x, currPos.y);
				for (ELandscapeType currType : types) {
					if (neighborType == currType) {
						found = true;
						break;
					}
				}

				if (!found) {
					return false;
				}
			}

			return true;
		}

		private final boolean isCornCuttable(int x, int y) {
			return objectsGrid.hasCuttableObject(x, y, EMapObjectType.CORN_ADULT);
		}

		@Override
		public void setDebugColor(int x, int y, Color color) {
			landscapeGrid.setDebugColor(x, y, color.getARGB());
		}

		@Override
		public short getBlockedPartition(int x, int y) {
			return landscapeGrid.getBlockedPartitionAt(x, y);
		}

	}

	final class GraphicsGrid implements IGraphicsGrid {
		@Override
		public final short getHeight() {
			return height;
		}

		@Override
		public final short getWidth() {
			return width;
		}

		@Override
		public final IMovable getMovableAt(int x, int y) {
			return movableGrid.getMovableAt(x, y);
		}

		@Override
		public final IMapObject getMapObjectsAt(int x, int y) {
			return objectsGrid.getObjectsAt(x, y);
		}

		@Override
		public final byte getHeightAt(int x, int y) {
			return landscapeGrid.getHeightAt(x, y);
		}

		@Override
		public final ELandscapeType getLandscapeTypeAt(int x, int y) {
			return landscapeGrid.getLandscapeTypeAt(x, y);
		}

		@Override
		public final int getDebugColorAt(int x, int y) {
			final int SCALE = 4;

			// int value = landscapeGrid.getBlockedPartitionAt(x, y) + 1;

			int value = partitionsGrid.getPartitionIdAt(x, y);

			// int value = partitionsGrid.getRealPartitionIdAt(x, y);

			// int value = partitionsGrid.getPlayerIdAt(x, y) + 1;

			// int value = partitionsGrid.getTowerCountAt(x, y) + 1;

			return Color.getABGR(((float) (value % SCALE)) / SCALE, ((float) ((value / SCALE) % SCALE)) / SCALE,
					((float) ((value / SCALE / SCALE) % SCALE)) / SCALE, 1);

			// return landscapeGrid.getDebugColor(x, y);

			// return flagsGrid.isMarked(x, y) ? Color.ORANGE.getARGB()
			// : (objectsGrid.getMapObjectAt(x, y, EMapObjectType.INFORMABLE_MAP_OBJECT) != null ? Color.GREEN.getARGB() : (objectsGrid
			// .getMapObjectAt(x, y, EMapObjectType.ATTACKABLE_TOWER) != null ? Color.RED.getARGB()
			// : (flagsGrid.isBlocked(x, y) ? Color.BLACK.getARGB() : (flagsGrid.isProtected(x, y) ? Color.BLUE.getARGB() : 0))));

			// return Color.BLACK.getARGB();

			// return objectsGrid.getMapObjectAt( x, y, EMapObjectType.ARROW) != null ? Color.RED.getABGR() : 0;
		}

		@Override
		public final boolean isBorder(int x, int y) {
			return flagsGrid.isBorderAt(x, y);
		}

		@Override
		public final byte getPlayerIdAt(int x, int y) {
			return partitionsGrid.getPlayerIdAt(x, y);
		}

		@Override
		public final byte getVisibleStatus(int x, int y) {
			return fogOfWar.getVisibleStatus(x, y);
		}

		@Override
		public final boolean isFogOfWarVisible(int x, int y) {
			return fogOfWar.isVisible(x, y);
		}

		@Override
		public final void setBackgroundListener(IGraphicsBackgroundListener backgroundListener) {
			landscapeGrid.setBackgroundListener(backgroundListener);
		}

		@Override
		public int nextDrawableX(int x, int y, int maxX) {
			return x + 1;
		}

		@Override
		public IPartitionSettings getPartitionSettings(int x, int y) {
			return partitionsGrid.getSettingsForManagerAt(x, y);
		}
	}

	final class MapObjectsManagerGrid implements IMapObjectsManagerGrid {
		private static final long serialVersionUID = 6223899915568781576L;

		@Override
		public final void setLandscape(int x, int y, ELandscapeType landscapeType) {
			setLandscapeTypeAt(x, y, landscapeType);
		}

		@Override
		public final void setBlocked(int x, int y, boolean blocked) {
			flagsGrid.setBlockedAndProtected(x, y, blocked);
		}

		@Override
		public final AbstractHexMapObject removeMapObjectType(int x, int y, EMapObjectType mapObjectType) {
			return objectsGrid.removeMapObjectType(x, y, mapObjectType);
		}

		@Override
		public final boolean removeMapObject(int x, int y, AbstractHexMapObject mapObject) {
			return objectsGrid.removeMapObject(x, y, mapObject);
		}

		@Override
		public final boolean isBlocked(int x, int y) {
			return flagsGrid.isBlocked(x, y);
		}

		@Override
		public final AbstractHexMapObject getMapObject(int x, int y, EMapObjectType mapObjectType) {
			return objectsGrid.getMapObjectAt(x, y, mapObjectType);
		}

		@Override
		public final void addMapObject(int x, int y, AbstractHexMapObject mapObject) {
			objectsGrid.addMapObjectAt(x, y, mapObject);
		}

		@Override
		public final short getWidth() {
			return width;
		}

		@Override
		public final short getHeight() {
			return height;
		}

		@Override
		public final boolean isInBounds(int x, int y) {
			return MainGrid.this.isInBounds(x, y);
		}

		@Override
		public final void setProtected(int x, int y, boolean protect) {
			flagsGrid.setProtected(x, y, protect);
		}

		@Override
		public EResourceType getRessourceTypeAt(int x, int y) {
			return landscapeGrid.getResourceTypeAt(x, y);
		}

		@Override
		public byte getRessourceAmountAt(int x, int y) {
			return landscapeGrid.getResourceAmountAt(x, y);
		}

		@Override
		public void hitWithArrowAt(ArrowObject arrow) {
			short x = arrow.getTargetX();
			short y = arrow.getTargetY();

			NewMovable movable = movableGrid.getMovableAt(x, y);
			if (movable != null) {
				movable.receiveHit(arrow.getHitStrength(), arrow.getSourcePos(), arrow.getShooterPlayerId());
				mapObjectsManager.removeMapObject(x, y, arrow);
			}
		}
	}

	final class EnclosedBlockedAreaFinderGrid implements IEnclosedBlockedAreaFinderGrid {
		@Override
		public final boolean isBlocked(int x, int y) {
			return MainGrid.this.isInBounds(x, y) && flagsGrid.isBlocked(x, y) && landscapeGrid.getBlockedPartitionAt(x, y) > 0;
		}

		@Override
		public final boolean isInBounds(int x, int y) {
			return MainGrid.this.isInBounds(x, y);
		}

		@Override
		public final short getPartitionAt(int x, int y) {
			return partitionsGrid.getPartitionIdAt(x, y);
		}

		@Override
		public final void setPartitionAt(int x, int y, short partition) {
			partitionsGrid.setPartitionAt(x, y, partition);
		}

		@Override
		public short getHeight() {
			return height;
		}

		@Override
		public short getWidth() {
			return width;
		}

	}

	final class ConstructionMarksGrid extends AbstractConstructionMarkableMap {
		@Override
		public final void setConstructMarking(int x, int y, boolean set, RelativePoint[] flattenPositions) {
			if (isInBounds(x, y)) {
				if (set) {
					mapObjectsManager.setConstructionMarking(x, y, getConstructionMarkValue(x, y, flattenPositions));
				} else {
					mapObjectsManager.setConstructionMarking(x, y, (byte) -1);
				}
			}
		}

		@Override
		public final short getWidth() {
			return width;
		}

		@Override
		public final short getHeight() {
			return height;
		}

		final boolean canConstructAt(short x, short y, EBuildingType type, Player player) {
			ELandscapeType[] landscapes = type.getGroundtypes();
			for (RelativePoint curr : type.getProtectedTiles()) {
				int currX = curr.calculateX(x);
				int currY = curr.calculateY(y);

				if (!canUsePositionForConstruction(currX, currY, landscapes, player.playerId)) {
					return false;
				}
			}
			return getConstructionMarkValue(x, y, type.getBlockedTiles()) >= 0;
		}

		@Override
		public final boolean canUsePositionForConstruction(int x, int y, ELandscapeType[] landscapeTypes, byte player) {
			return isInBounds(x, y) && !flagsGrid.isProtected(x, y) && partitionsGrid.getPlayerIdAt(x, y) == player
					&& isAllowedLandscape(x, y, landscapeTypes);
		}

		private final boolean isAllowedLandscape(int x, int y, ELandscapeType[] landscapes) {
			ELandscapeType landscapeAt = landscapeGrid.getLandscapeTypeAt(x, y);
			for (byte i = 0; i < landscapes.length; i++) {
				if (landscapeAt == landscapes[i]) {
					return true;
				}
			}
			return false;
		}

		private byte getConstructionMarkValue(int mapX, int mapY, final RelativePoint[] flattenPositions) {
			int sum = 0;

			for (RelativePoint currPos : flattenPositions) {
				sum += landscapeGrid.getHeightAt(currPos.calculateX(mapX), currPos.calculateY(mapY));
			}

			float avg = ((float) sum) / flattenPositions.length;
			float diff = 0;

			for (RelativePoint currPos : flattenPositions) {
				float currDiff = Math.abs(landscapeGrid.getHeightAt(currPos.calculateX(mapX), currPos.calculateY(mapY)) - avg);
				diff += currDiff;
			}

			int result = (int) (Constants.CONSTRUCTION_MARK_SCALE_FACTOR * Math.pow(diff, Constants.CONSTRUCTION_MARK_POW_FACTOR) / flattenPositions.length);

			if (result <= Byte.MAX_VALUE) {
				return (byte) result;
			} else {
				return -1;
			}
		}
	}

	final class MovablePathfinderGrid extends AbstractNewMovableGrid {
		private static final long serialVersionUID = 4006228724969442801L;

		private transient PathfinderGrid pathfinderGrid;

		private transient AbstractAStar aStar;
		transient DijkstraAlgorithm dijkstra; // not private, because it's used by BuildingsGrid
		private transient InAreaFinder inAreaFinder;

		public MovablePathfinderGrid() {
			initPathfinders();
		}

		private final void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
			ois.defaultReadObject();
			initPathfinders();
		}

		private final void initPathfinders() {
			pathfinderGrid = new PathfinderGrid();

			aStar = new BucketQueueAStar(pathfinderGrid, width, height);
			dijkstra = new DijkstraAlgorithm(pathfinderGrid, aStar, width, height);
			inAreaFinder = new InAreaFinder(pathfinderGrid, width, height);
		}

		@Override
		public final boolean isBlocked(short x, short y) {
			return flagsGrid.isBlocked(x, y);
		}

		@Override
		public final boolean isProtected(short x, short y) {
			return flagsGrid.isProtected(x, y);
		}

		@Override
		public final boolean isBlockedOrProtected(short x, short y) {
			return isBlocked(x, y) || isProtected(x, y);
		}

		@Override
		public final boolean canPop(ShortPoint2D position, EMaterialType material) {
			return mapObjectsManager.canPop(position.x, position.y, material);
		}

		@Override
		public final byte getHeightAt(ShortPoint2D position) {
			return landscapeGrid.getHeightAt(position.x, position.y);
		}

		@Override
		public final void setMarked(ShortPoint2D position, boolean marked) {
			flagsGrid.setMarked(position.x, position.y, marked);
		}

		@Override
		public final boolean isMarked(ShortPoint2D position) {
			return flagsGrid.isMarked(position.x, position.y);
		}

		@Override
		public final boolean executeSearchType(ShortPoint2D position, ESearchType searchType) {
			return mapObjectsManager.executeSearchType(position, searchType);
		}

		@Override
		public final void placeSmoke(ShortPoint2D pos, boolean place) {
			if (place) {
				mapObjectsManager.addSimpleMapObject(pos, EMapObjectType.SMOKE, false, null);
			} else {
				mapObjectsManager.removeMapObjectType(pos.x, pos.y, EMapObjectType.SMOKE);
			}
		}

		@Override
		public void changePlayerAt(ShortPoint2D position, Player player) {
			partitionsGrid.changePlayerAt(position, player.playerId);
			bordersThread.checkPosition(position);

			checkPositionThatChangedPlayer(position);
		}

		@Override
		public final boolean isValidPosition(IPathCalculateable pathRequester, ShortPoint2D pos) {
			short x = pos.x, y = pos.y;
			return MainGrid.this.isInBounds(x, y) && !isBlocked(x, y)
					&& (!pathRequester.needsPlayersGround() || pathRequester.getPlayerId() == partitionsGrid.getPlayerIdAt(x, y));
		}

		@Override
		public float getResourceAmountAround(short x, short y, EResourceType type) {
			return landscapeGrid.getResourceAmountAround(x, y, type);
		}

		@Override
		public void addJobless(IManageableBearer bearer) {
			partitionsGrid.getPartitionAt(bearer).addJobless(bearer);
		}

		@Override
		public void removeJobless(IManageableBearer bearer) {
			partitionsGrid.getPartitionAt(bearer).removeJobless(bearer);
		}

		@Override
		public void addJobless(IManageableWorker worker) {
			partitionsGrid.getPartitionAt(worker).addJobless(worker);
		}

		@Override
		public void removeJobless(IManageableWorker worker) {
			partitionsGrid.getPartitionAt(worker).removeJobless(worker);
		}

		@Override
		public void addJobless(IManageableDigger digger) {
			partitionsGrid.getPartitionAt(digger).addJobless(digger);
		}

		@Override
		public void removeJobless(IManageableDigger digger) {
			partitionsGrid.getPartitionAt(digger).removeJobless(digger);
		}

		@Override
		public void addJobless(IManageableBricklayer bricklayer) {
			partitionsGrid.getPartitionAt(bricklayer).addJobless(bricklayer);
		}

		@Override
		public void removeJobless(IManageableBricklayer bricklayer) {
			partitionsGrid.getPartitionAt(bricklayer).removeJobless(bricklayer);
		}

		@Override
		public boolean takeMaterial(ShortPoint2D position, EMaterialType materialType) {
			short x = position.x;
			short y = position.y;
			if (mapObjectsManager.popMaterial(x, y, materialType)) {
				return true;
			} else
				return false;
		}

		@Override
		public boolean dropMaterial(ShortPoint2D position, EMaterialType materialType, boolean offer) {
			if (mapObjectsManager.pushMaterial(position.x, position.y, materialType)) {
				if (offer) {
					partitionsGrid.getPartitionAt(position.x, position.y).addOffer(position, materialType);
				}
				return true;
			} else
				return false;
		}

		@Override
		public EDirection getDirectionOfSearched(ShortPoint2D position, ESearchType searchType) {
			if (searchType == ESearchType.FISHABLE) {
				for (EDirection direction : EDirection.values) {
					ShortPoint2D currPos = direction.getNextHexPoint(position);
					short x = currPos.x, y = currPos.y;

					if (isInBounds(x, y) && landscapeGrid.getLandscapeTypeAt(x, y).isWater()) {
						return direction;
					}
				}
				return null;
			} else if (searchType == ESearchType.RIVER) {
				for (EDirection direction : EDirection.values) {
					ShortPoint2D currPos = direction.getNextHexPoint(position);
					short x = currPos.x, y = currPos.y;
					ELandscapeType landscapeTypeAt = landscapeGrid.getLandscapeTypeAt(x, y);

					if (isInBounds(x, y)
							&& (landscapeTypeAt == ELandscapeType.RIVER1 || landscapeTypeAt == ELandscapeType.RIVER2
									|| landscapeTypeAt == ELandscapeType.RIVER3 || landscapeTypeAt == ELandscapeType.RIVER4)) {
						return direction;
					}
				}
				return null;
			} else {
				return null;
			}
		}

		@Override
		public EMaterialType popToolProductionRequest(ShortPoint2D pos) {
			return partitionsGrid.getPartitionAt(pos.x, pos.y).popToolProduction(pos);
		}

		@Override
		public final boolean isPigAdult(ShortPoint2D pos) {
			return mapObjectsManager.isPigAdult(pos);
		}

		@Override
		public void placePigAt(ShortPoint2D pos, boolean place) {
			mapObjectsManager.placePig(pos, place);
		}

		@Override
		public boolean hasPigAt(ShortPoint2D position) {
			return mapObjectsManager.isPigThere(position);
		}

		@Override
		public boolean canPushMaterial(ShortPoint2D position) {
			return mapObjectsManager.canPush(position);
		}

		@Override
		public void changeHeightTowards(short x, short y, byte targetHeight) {
			landscapeGrid.flattenAndChangeHeightTowards(x, y, targetHeight);
			objectsGrid.removeMapObjectType(x, y, EMapObjectType.CORN_ADULT);
			objectsGrid.removeMapObjectType(x, y, EMapObjectType.CORN_DEAD);
			objectsGrid.removeMapObjectType(x, y, EMapObjectType.CORN_GROWING);
		}

		@Override
		public boolean hasNoMovableAt(short x, short y) {
			return movableGrid.hasNoMovableAt(x, y);
		}

		@Override
		public boolean isFreePosition(ShortPoint2D position) {
			short x = position.x;
			short y = position.y;

			return isInBounds(x, y) && !flagsGrid.isBlocked(x, y) && movableGrid.hasNoMovableAt(x, y);
		}

		@Override
		public void leavePosition(ShortPoint2D position, NewMovable movable) {
			movableGrid.movableLeft(position, movable);
		}

		@Override
		public void enterPosition(ShortPoint2D position, NewMovable movable, boolean informFullArea) {
			movableGrid.movableEntered(position, movable);

			if (movable.isAttackable()) {
				movableGrid.informMovables(movable, position.x, position.y, informFullArea);
				objectsGrid.informObjectsAboutAttackble(position, movable, informFullArea, !EMovableType.isBowman(movable.getMovableType()));
			}
		}

		@Override
		public Path calculatePathTo(IPathCalculateable pathRequester, ShortPoint2D targetPos) {
			return aStar.findPath(pathRequester, targetPos);
		}

		@Override
		public Path searchDijkstra(IPathCalculateable pathCalculateable, short centerX, short centerY, short radius, ESearchType searchType) {
			return dijkstra.find(pathCalculateable, centerX, centerY, (short) 1, radius, searchType);
		}

		@Override
		public Path searchInArea(IPathCalculateable pathCalculateable, short centerX, short centerY, short radius, ESearchType searchType) {
			ShortPoint2D target = inAreaFinder.find(pathCalculateable, centerX, centerY, radius, searchType);
			if (target != null) {
				return calculatePathTo(pathCalculateable, target);
			} else {
				return null;
			}
		}

		@Override
		public NewMovable getMovableAt(short x, short y) {
			return movableGrid.getMovableAt(x, y);
		}

		@Override
		public void addSelfDeletingMapObject(ShortPoint2D position, EMapObjectType mapObjectType, float duration, Player player) {
			mapObjectsManager.addSelfDeletingMapObject(position, mapObjectType, duration, player);
		}

		@Override
		public boolean isInBounds(short x, short y) {
			return MainGrid.this.isInBounds(x, y);
		}

		@Override
		public boolean fitsSearchType(IPathCalculateable pathCalculable, ShortPoint2D pos, ESearchType searchType) {
			return pathfinderGrid.fitsSearchType(pos.x, pos.y, searchType, pathCalculable);
		}

		@Override
		public ELandscapeType getLandscapeTypeAt(short x, short y) {
			return landscapeGrid.getLandscapeTypeAt(x, y);
		}

		@Override
		public IAttackable getEnemyInSearchArea(ShortPoint2D position, IAttackable searchingAttackable, short searchRadius) {
			boolean isBowman = EMovableType.isBowman(searchingAttackable.getMovableType());

			IAttackable enemy = getEnemyInSearchArea(searchingAttackable.getPlayerId(), new HexGridArea(position.x, position.y, (short) 1,
					searchRadius), isBowman);
			if (enemy == null && !isBowman) {
				enemy = getEnemyInSearchArea(searchingAttackable.getPlayerId(), new HexGridArea(position.x, position.y, searchRadius,
						Constants.TOWER_SEARCH_RADIUS), isBowman);
			}

			return enemy;
		}

		private IAttackable getEnemyInSearchArea(byte searchingPlayer, HexGridArea area, boolean isBowman) {
			for (ShortPoint2D curr : area) {
				short x = curr.x;
				short y = curr.y;

				if (0 <= x && x < width && 0 <= y && y < height) {
					IAttackable currAttackable = movableGrid.getMovableAt(x, y);
					if (currAttackable == null && !isBowman) {
						currAttackable = (IAttackable) objectsGrid.getMapObjectAt(x, y, EMapObjectType.ATTACKABLE_TOWER);
					}

					if (currAttackable != null && MovableGrid.isEnemy(searchingPlayer, currAttackable)) {
						return currAttackable;
					}
				}
			}

			return null;
		}

		@Override
		public void addArrowObject(ShortPoint2D attackedPos, ShortPoint2D shooterPos, byte shooterPlayerId, float hitStrength) {
			mapObjectsManager.addArrowObject(attackedPos, shooterPos, shooterPlayerId, hitStrength);
		}

		@Override
		public final ShortPoint2D calcDecentralizeVector(short x, short y) {
			HexGridArea area = new HexGridArea(x, y, (short) 1, Constants.MOVABLE_FLOCK_TO_DECENTRALIZE_MAX_RADIUS);
			HexGridAreaIterator iter = area.iterator();
			int dx = 0, dy = 0;

			while (iter.hasNext()) {
				short radius = iter.getRadiusOfNext();
				ShortPoint2D curr = iter.next();
				short currX = curr.x;
				short currY = curr.y;

				int factor;

				if (!MainGrid.this.isInBounds(currX, currY) || flagsGrid.isBlocked(currX, currY)) {
					factor = radius == 1 ? 6 : 2;
				} else if (!movableGrid.hasNoMovableAt(currX, currY)) {
					factor = Constants.MOVABLE_FLOCK_TO_DECENTRALIZE_MAX_RADIUS - radius + 1;
				} else {
					continue;
				}

				dx += (x - currX) * factor;
				dy += (y - currY) * factor;
			}

			return new ShortPoint2D(dx, dy);
		}

		@Override
		public Player getPlayerAt(ShortPoint2D position) {
			return partitionsGrid.getPlayerAt(position.x, position.y);
		}
	}

	final class BordersThreadGrid implements IBordersThreadGrid {
		@Override
		public final byte getPlayerIdAt(short x, short y) {
			return partitionsGrid.getPlayerIdAt(x, y);
		}

		@Override
		public final void setBorderAt(short x, short y, boolean isBorder) {
			flagsGrid.setBorderAt(x, y, isBorder);
		}

		@Override
		public final boolean isInBounds(short x, short y) {
			return MainGrid.this.isInBounds(x, y);
		}

		@Override
		public final short getBlockedPartition(short x, short y) {
			return landscapeGrid.getBlockedPartitionAt(x, y);
		}
	}

	final class BuildingsGrid implements IBuildingsGrid, Serializable {
		private static final long serialVersionUID = -5567034251907577276L;

		private final RequestStackGrid requestStackGrid = new RequestStackGrid();

		@Override
		public final byte getHeightAt(ShortPoint2D position) {
			return landscapeGrid.getHeightAt(position.x, position.y);
		}

		@Override
		public final void pushMaterialsTo(ShortPoint2D position, EMaterialType type, byte numberOf) {
			for (int i = 0; i < numberOf; i++) {
				movablePathfinderGrid.dropMaterial(position, type, true);
			}
		}

		@Override
		public final boolean setBuilding(ShortPoint2D position, Building newBuilding) {
			if (MainGrid.this.isInBounds(position.x, position.y)) {
				FreeMapArea protectedArea = new FreeMapArea(position, newBuilding.getBuildingType().getProtectedTiles());

				if (canConstructAt(protectedArea)) {
					setProtectedState(protectedArea, true);
					mapObjectsManager.addBuildingTo(position, newBuilding);
					objectsGrid.setBuildingArea(new FreeMapArea(position, newBuilding.getBuildingType().getBlockedTiles()), newBuilding);
					return true;
				} else {
					return false;
				}
			} else {
				return false;
			}
		}

		private final void setProtectedState(FreeMapArea area, boolean setProtected) {
			for (ShortPoint2D curr : area) {
				if (MainGrid.this.isInBounds(curr.x, curr.y))
					flagsGrid.setProtected(curr.x, curr.y, setProtected);
			}
		}

		private final boolean canConstructAt(FreeMapArea area) {
			for (ShortPoint2D curr : area) {
				short x = curr.x;
				short y = curr.y;

				if (!isInBounds(x, y) || flagsGrid.isProtected(x, y) || flagsGrid.isBlocked(x, y)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public final void removeBuildingAt(ShortPoint2D pos) {
			IBuilding building = (IBuilding) objectsGrid.getMapObjectAt(pos.x, pos.y, EMapObjectType.BUILDING);
			mapObjectsManager.removeMapObjectType(pos.x, pos.y, EMapObjectType.BUILDING);

			FreeMapArea area = new FreeMapArea(pos, building.getBuildingType().getProtectedTiles());

			objectsGrid.setBuildingArea(area, null);

			for (ShortPoint2D curr : area) {
				short x = curr.x;
				short y = curr.y;
				if (isInBounds(x, y)) {
					flagsGrid.setBlockedAndProtected(x, y, false);
					landscapeGrid.activateUnflattening(x, y);
				}
			}
		}

		@Override
		public final void setBlocked(FreeMapArea area, boolean blocked) {
			for (ShortPoint2D curr : area) {
				if (MainGrid.this.isInBounds(curr.x, curr.y))
					flagsGrid.setBlockedAndProtected(curr.x, curr.y, blocked);
			}
		}

		@Override
		public final short getWidth() {
			return width;
		}

		@Override
		public final short getHeight() {
			return height;
		}

		@Override
		public final NewMovable getMovable(ShortPoint2D position) {
			return movableGrid.getMovableAt(position.x, position.y);
		}

		@Override
		public final MapObjectsManager getMapObjectsManager() {
			return mapObjectsManager;
		}

		@Override
		public final AbstractNewMovableGrid getMovableGrid() {
			return movablePathfinderGrid;
		}

		@Override
		public final void requestDiggers(IDiggerRequester requester, byte amount) {
			partitionsGrid.getPartitionAt(requester).requestDiggers(requester, amount);
		}

		@Override
		public final void requestBricklayer(Building building, ShortPoint2D bricklayerTargetPos, EDirection direction) {
			partitionsGrid.getPartitionAt(building).requestBricklayer(building, bricklayerTargetPos, direction);
		}

		@Override
		public final IRequestsStackGrid getRequestStackGrid() {
			return requestStackGrid;
		}

		@Override
		public final void requestBuildingWorker(EMovableType workerType, WorkerBuilding workerBuilding) {
			partitionsGrid.getPartitionAt(workerBuilding).requestBuildingWorker(workerType, workerBuilding);
		}

		@Override
		public final void requestSoilderable(IBarrack barrack) {
			partitionsGrid.getPartitionAt(barrack).requestSoilderable(barrack);
		}

		@Override
		public final DijkstraAlgorithm getDijkstra() {
			return movablePathfinderGrid.dijkstra;
		}

		private class RequestStackGrid implements IRequestsStackGrid, Serializable {
			private static final long serialVersionUID = 1278397366408051067L;

			@Override
			public final void request(EMaterialType materialType, MaterialRequestObject requestObject) {
				partitionsGrid.getPartitionAt(requestObject).request(materialType, requestObject);
			}

			@Override
			public final boolean hasMaterial(ShortPoint2D position, EMaterialType materialType) {
				return mapObjectsManager.canPop(position.x, position.y, materialType);
			}

			@Override
			public final void popMaterial(ShortPoint2D position, EMaterialType materialType) {
				mapObjectsManager.popMaterial(position.x, position.y, materialType);
			}

			@Override
			public final byte getStackSize(ShortPoint2D position, EMaterialType materialType) {
				return mapObjectsManager.getStackSize(position.x, position.y, materialType);
			}

			@Override
			// FIXME @Andreas Eberle: implement check to prevent multiple offers for the same material
			public final void createOffersForAvailableMaterials(ShortPoint2D position, EMaterialType materialType) {
				byte stackSize = mapObjectsManager.getStackSize(position.x, position.y, materialType);
				PartitionManager partition = partitionsGrid.getPartitionAt(position.x, position.y);
				for (byte i = 0; i < stackSize; i++) {
					partition.addOffer(position, materialType);
				}
			}
		}

		@Override
		public void occupyAreaByTower(Player player, MapCircle influencingArea) {
			partitionsGrid.addTowerAndOccupyArea(player.playerId, influencingArea);
			checkAllPositionsForEnclosedBlockedAreas(influencingArea); // TODO @Andreas Eberle only test the borders of changed areas!!
		}

		@Override
		public void freeAreaOccupiedByTower(ShortPoint2D towerPosition) {
			Iterable<ShortPoint2D> positions = partitionsGrid.removeTowerAndFreeOccupiedArea(towerPosition);
			checkAllPositionsForEnclosedBlockedAreas(positions);
		}

		@Override
		public void changePlayerOfTower(ShortPoint2D towerPosition, Player newPlayer, FreeMapArea groundArea) {
			Iterable<ShortPoint2D> positions = partitionsGrid.changePlayerOfTower(towerPosition, newPlayer.playerId, groundArea);
			checkAllPositionsForEnclosedBlockedAreas(positions);
		}

		private void checkAllPositionsForEnclosedBlockedAreas(Iterable<ShortPoint2D> area) {
			for (ShortPoint2D curr : area) {
				checkPositionThatChangedPlayer(curr);
			}
		}

		@Override
		public boolean isAreaFlattenedAtHeight(ShortPoint2D position, RelativePoint[] positions, byte expectedHeight) {
			return landscapeGrid.isAreaFlattenedAtHeight(position, positions, expectedHeight);
		}
	}

	final class GUIInputGrid implements IGuiInputGrid {
		@Override
		public final NewMovable getMovable(short x, short y) {
			return movableGrid.getMovableAt(x, y);
		}

		@Override
		public final short getWidth() {
			return width;
		}

		@Override
		public final short getHeight() {
			return height;
		}

		@Override
		public final IBuilding getBuildingAt(short x, short y) {
			return (IBuilding) objectsGrid.getMapObjectAt(x, y, EMapObjectType.BUILDING);
		}

		@Override
		public final boolean isInBounds(ShortPoint2D position) {
			return MainGrid.this.isInBounds(position.x, position.y);
		}

		@Override
		public final void resetDebugColors() {
			landscapeGrid.resetDebugColors();
		}

		@Override
		public final ShortPoint2D getConstructablePosition(ShortPoint2D pos, EBuildingType type, byte playerId, boolean useNeighbors) {
			Player player;
			if (CommonConstants.ENABLE_ALL_PLAYER_SELECTION) {
				player = partitionsGrid.getPlayerAt(pos.x, pos.y);
			} else {
				player = partitionsGrid.getPlayer(playerId);
			}

			if (player != null) {
				if (constructionMarksGrid.canConstructAt(pos.x, pos.y, type, player)) {
					return pos;
				} else if (useNeighbors) {
					for (ShortPoint2D neighbour : new MapNeighboursArea(pos)) {
						if (constructionMarksGrid.canConstructAt(neighbour.x, neighbour.y, type, player)) {
							return neighbour;
						}
					}
					return null;
				}
			}

			return null;
		}

		@Override
		public final void save(UIState uiState) throws FileNotFoundException, IOException, InterruptedException {
			boolean savedPausingState = MatchConstants.clock.isPausing();
			MatchConstants.clock.setPausing(true);
			try {
				Thread.sleep(100); // FIXME @Andreas serializer should wait until threads did their work!
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			MapList list = MapList.getDefaultList();
			list.saveMap(uiState, MainGrid.this);

			MatchConstants.clock.setPausing(savedPausingState);
		}

		@Override
		public final void toggleFogOfWar() {
			fogOfWar.toggleEnabled();
		}

		@Override
		public AbstractConstructionMarkableMap getConstructionMarksGrid() {
			return constructionMarksGrid;
		}

		@Override
		public void constructBuildingAt(ShortPoint2D position, EBuildingType type) {
			MainGrid.this.constructBuildingAt(position, type, partitionsGrid.getPlayerAt(position.x, position.y), false);
		}

		@Override
		public void postionClicked(short x, short y) {
			System.out.println("clicked pos (" + x + "|" + y + "):  player: " + partitionsGrid.getPlayerIdAt(x, y) + "  partition: "
					+ partitionsGrid.getPartitionIdAt(x, y) + "  real partition: " + partitionsGrid.getRealPartitionIdAt(x, y) + "  towerCount: "
					+ partitionsGrid.getTowerCountAt(x, y));
		}

		@Override
		public void setMaterialDistributionSettings(ShortPoint2D managerPosition, EMaterialType materialType, float[] probabilities) {
			if (isInBounds(managerPosition))
				partitionsGrid.getSettingsForManagerAt(managerPosition.x, managerPosition.y).getDistributionSettings(materialType)
						.setProbabilities(probabilities);
		}

		@Override
		public void setMaterialPrioritiesSetting(ShortPoint2D managerPosition, EMaterialType[] materialTypeForPriority) {
			if (isInBounds(managerPosition))
				partitionsGrid.getSettingsForManagerAt(managerPosition.x, managerPosition.y).setMaterialTypesForPriorities(materialTypeForPriority);
		}

		@Override
		public short getBlockedPartition(ShortPoint2D position) {
			return landscapeGrid.getBlockedPartitionAt(position.x, position.y);
		}

		@Override
		public boolean isBlocked(ShortPoint2D position) {
			return flagsGrid.isBlocked(position.x, position.y);
		}

		@Override
		public Player getPlayer(byte playerId) {
			return partitionsGrid.getPlayer(playerId);
		}
	}

	/**
	 * This class implements the {@link IPlayerChangedListener} interface and executes all work that needs to be done when a position of the grid
	 * changes it's player.
	 * 
	 * @author Andreas Eberle
	 * 
	 */
	final class PlayerChangedListener implements IPlayerChangedListener {

		@Override
		public void playerChangedAt(int x, int y, byte newPlayerId) {
			final ShortPoint2D position = new ShortPoint2D(x, y);
			bordersThread.checkPosition(position);

			Building building = objectsGrid.getBuildingOn(x, y);
			if (building != null && building.getPlayerId() != newPlayerId) {
				building.kill();
			}
		}
	}

	final class FogOfWarGrid implements IFogOfWarGrid {
		@Override
		public final IMovable getMovableAt(short x, short y) {
			return movableGrid.getMovableAt(x, y);
		}

		@Override
		public final IMapObject getMapObjectsAt(short x, short y) {
			return objectsGrid.getObjectsAt(x, y);
		}

		@Override
		public final ConcurrentLinkedQueue<? extends IViewDistancable> getMovableViewDistancables() {
			return NewMovable.getAllMovables();
		}

		@Override
		public final ConcurrentLinkedQueue<? extends IViewDistancable> getBuildingViewDistancables() {
			return Building.getAllBuildings();
		}
	}

}
