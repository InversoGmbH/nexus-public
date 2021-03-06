/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.common.entity.EntityHelper;
import org.sonatype.nexus.common.entity.EntityId;
import org.sonatype.nexus.orient.testsupport.DatabaseInstanceRule;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.index.OCompositeKey;
import com.orientechnologies.orient.core.index.OIndexCursor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.sonatype.nexus.repository.storage.MetadataNodeEntityAdapter.P_ATTRIBUTES;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class AssetStoreImplTest
    extends TestSupport
{
  @Rule
  public DatabaseInstanceRule database = DatabaseInstanceRule.inMemory("test");

  private AssetEntityAdapter assetEntityAdapter;

  private BucketEntityAdapter bucketEntityAdapter;

  private ComponentEntityAdapter componentEntityAdapter;

  private AssetStoreImpl underTest;

  private Bucket bucket;

  @Before
  public void setUp() {
    bucketEntityAdapter = new BucketEntityAdapter();
    componentEntityAdapter = new ComponentEntityAdapter(bucketEntityAdapter);
    assetEntityAdapter = new AssetEntityAdapter(bucketEntityAdapter, componentEntityAdapter);

    underTest = new AssetStoreImpl(database.getInstanceProvider(), assetEntityAdapter);

    try (ODatabaseDocumentTx db = database.getInstance().connect()) {
      bucketEntityAdapter.register(db);
      componentEntityAdapter.register(db);
      assetEntityAdapter.register(db);
      bucket = new Bucket();
      bucket.attributes(new NestedAttributesMap(P_ATTRIBUTES, new HashMap<>()));
      bucket.setRepositoryName("test-repo");
      bucketEntityAdapter.addEntity(db, bucket);
    }
  }

  @Test
  public void getNextPageReturnsEntriesByPages() {
    int limit = 2;

    Asset asset1 = createAsset("asset1");
    Asset asset2 = createAsset("asset2");
    Asset asset3 = createAsset("asset3");

    try (ODatabaseDocumentTx db = database.getInstance().connect()) {
      assetEntityAdapter.addEntity(db, asset1);
      assetEntityAdapter.addEntity(db, asset2);
      assetEntityAdapter.addEntity(db, asset3);
    }

    OIndexCursor cursor = underTest.getIndex(AssetEntityAdapter.I_BUCKET_COMPONENT_NAME).cursor();

    List<Entry<OCompositeKey, EntityId>> assetPage1 = underTest.getNextPage(cursor, limit);
    List<Entry<OCompositeKey, EntityId>> assetPage2 = underTest.getNextPage(cursor, limit);
    assertThat(assetPage1.size(), is(2));
    assertThat(assetPage1.get(0).getValue(), is(EntityHelper.id(asset1)));
    assertThat(assetPage1.get(1).getValue(), is(EntityHelper.id(asset2)));
    assertThat(assetPage2.size(), is(1));
    assertThat(assetPage2.get(0).getValue(), is(EntityHelper.id(asset3)));
  }

  @Test
  public void getNextPageStopsAtNullEntry() {
    int limit = 2;

    Asset asset1 = createAsset("asset1");

    try (ODatabaseDocumentTx db = database.getInstance().connect()) {
      assetEntityAdapter.addEntity(db, asset1);
    }

    OIndexCursor cursor = underTest.getIndex(AssetEntityAdapter.I_BUCKET_COMPONENT_NAME).cursor();

    List<Entry<OCompositeKey, EntityId>> assetPage1 = underTest.getNextPage(cursor, limit);
    List<Entry<OCompositeKey, EntityId>> assetPage2 = underTest.getNextPage(cursor, limit);

    assertThat(assetPage1.size(), is(1));
    assertThat(assetPage1.get(0).getValue(), is(EntityHelper.id(asset1)));
  }

  private Asset createAsset(final String name) {
    Asset asset = new Asset();
    asset.bucketId(EntityHelper.id(bucket));
    asset.format("maven2");
    asset.attributes(new NestedAttributesMap(P_ATTRIBUTES, new HashMap<>()));
    asset.name(name);
    return asset;
  }
}
