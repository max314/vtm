/*
 * Copyright 2012 Hannes Janetzek
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.oscim.renderer.overlays;

import org.oscim.core.MapPosition;
import org.oscim.core.Tile;
import org.oscim.renderer.MapTile;
import org.oscim.renderer.TileManager;
import org.oscim.renderer.Tiles;
import org.oscim.renderer.layer.TextItem;
import org.oscim.renderer.layer.TextLayer;
import org.oscim.utils.FastMath;
import org.oscim.utils.PausableThread;
import org.oscim.view.MapView;

import android.os.SystemClock;
import android.util.FloatMath;

public class OverlayText extends RenderOverlay {

	private Tiles tiles;
	private LabelThread mThread;

	private MapPosition mWorkPos;
	private TextLayer mWorkLayer;
	private TextLayer mNewLayer;

	/* package */boolean mRun;
	/* package */boolean mRerun;

	class LabelThread extends PausableThread {

		@Override
		protected void doWork() {
			SystemClock.sleep(300);
			mRun = false;
			updateLabels();
			mMapView.redrawMap();
		}

		@Override
		protected String getThreadName() {
			return "Labeling";
		}

		@Override
		protected boolean hasWork() {
			return mRun || mRerun;
		}
	}

	public OverlayText(MapView mapView) {
		super(mapView);

		mWorkPos = new MapPosition();
		mThread = new LabelThread();
		mThread.start();
	}

	void updateLabels() {
		tiles = TileManager.getActiveTiles(tiles);

		// Log.d("...", "relabel " + mRerun + " " + x + " " + y);
		if (tiles.cnt == 0)
			return;

		mMapView.getMapViewPosition().getMapPosition(mWorkPos, null);

		TextLayer tl = mWorkLayer;

		if (tl == null)
			tl = new TextLayer();

		// tiles might be from another zoomlevel than the current:
		// this scales MapPosition to the zoomlevel of tiles...
		// TODO create a helper function in MapPosition
		int diff = tiles.tiles[0].zoomLevel - mWorkPos.zoomLevel;

		if (diff > 1 || diff < -2) {
			synchronized (this) {
				mNewLayer = tl;
			}
			return;
		}

		float div = FastMath.pow(diff);

		// fix map position to tile coordinates
		float size = Tile.TILE_SIZE;
		int x = (int) (mWorkPos.x / div / size);
		int y = (int) (mWorkPos.y / div / size);
		mWorkPos.x = x * size;
		mWorkPos.y = y * size;
		mWorkPos.zoomLevel += diff;
		mWorkPos.scale = div;

		float angle = (float) Math.toRadians(mWorkPos.angle);
		float cos = FloatMath.cos(angle);
		float sin = FloatMath.sin(angle);

		// TODO more sophisticated placement :)
		for (int i = 0, n = tiles.cnt; i < n; i++) {
			MapTile t = tiles.tiles[i];
			if (!t.isVisible)
				continue;

			int dx = (t.tileX - x) * Tile.TILE_SIZE;
			int dy = (t.tileY - y) * Tile.TILE_SIZE;

			for (TextItem ti = t.labels; ti != null; ti = ti.next) {

				TextItem ti2 = TextItem.get().move(ti, dx, dy);

				if (!ti.text.caption) {
					if (cos * (ti.x2 - ti.x1) - sin * (ti.y2 - ti.y1) < 0) {
						// flip label upside-down
						ti2.x1 = ti.x2;
						ti2.y1 = ti.y2;
						ti2.x2 = ti.x1;
						ti2.y2 = ti.y1;
					} else {
						ti2.x1 = ti.x1;
						ti2.y1 = ti.y1;
						ti2.x2 = ti.x2;
						ti2.y2 = ti.y2;
					}
				}

				tl.addText(ti2);
			}
		}

		// draw text to bitmaps and create vertices
		tl.prepare();

		// everything synchronized?
		synchronized (this) {
			mNewLayer = tl;
		}
	}

	@Override
	public synchronized void update(MapPosition curPos, boolean positionChanged, boolean tilesChanged) {
		// Log.d("...", "update " + tilesChanged + " " + positionChanged);

		if (mNewLayer != null) {

			// keep text layer, not recrating its canvas each time...
			mWorkLayer = (TextLayer) layers.textureLayers;
			layers.clear();

			layers.textureLayers = mNewLayer;
			mNewLayer = null;

			// make the 'labeled' MapPosition current
			MapPosition tmp = mMapPosition;
			mMapPosition = mWorkPos;
			mWorkPos = tmp;

			// TODO should return true instead
			newData = true;
		}

		if (tilesChanged || positionChanged) {
			if (!mRun) {
				mRun = true;
				synchronized (mThread) {
					mThread.notify();
				}
			}
		}
	}
}
