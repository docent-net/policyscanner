/**
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.security.scanner.actions.extractors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.cloudresourcemanager.CloudResourceManager.Projects;
import com.google.api.services.cloudresourcemanager.CloudResourceManager.Projects.GetIamPolicy;
import com.google.api.services.cloudresourcemanager.model.Binding;
import com.google.api.services.cloudresourcemanager.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.model.Policy;
import com.google.cloud.dataflow.sdk.transforms.DoFnTester;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.security.scanner.primitives.GCPProject;
import com.google.cloud.security.scanner.primitives.GCPResource;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy;
import com.google.cloud.security.scanner.primitives.GCPResourcePolicy.PolicyBinding;
import com.google.cloud.security.scanner.primitives.GCPResourceState;
import com.google.cloud.security.scanner.primitives.PoliciedObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for ExtractState */
@RunWith(JUnit4.class)
public class ExtractStateTest {
  private static final String ROLE = "OWNER";
  private static final String MEMBER_1 = "user:test@test.test";
  private static final String MEMBER_2 = "serviceAccount:wow@wow.wow";
  private Projects projectsApiObject;
  private GetIamPolicy getIamPolicy;
  private DoFnTester<GCPProject, KV<GCPResource, GCPResourceState>> tester;

  @Before
  public void setUp() throws IOException {
    GCPProject.setProjectsApiStub(this.projectsApiObject);
    this.projectsApiObject = mock(Projects.class);
    this.getIamPolicy = mock(Projects.GetIamPolicy.class);
    this.tester = DoFnTester.of(new ExtractState());
    when(this.projectsApiObject.getIamPolicy(anyString(), any(GetIamPolicyRequest.class)))
        .thenReturn(this.getIamPolicy);
  }

  @Test
  public void testOneElement() throws IOException {
    GCPProject project = getSampleProject("");
    List<GCPProject> projects = new ArrayList<>(1);
    projects.add(project);

    GCPProject.setProjectsApiStub(this.projectsApiObject);
    when(this.getIamPolicy.execute()).thenReturn(getSamplePolicy(1));
    List<KV<GCPResource, GCPResourceState>> results = this.tester.processBatch(projects);
    assertEquals(results.size(), 1);
    assertEquals(results.get(0).getKey(), project);
    assertEquals(results.get(0).getValue(), getSampleGCPResourcePolicy(project));
  }

  @Test
  public void testMultipleElements() throws IOException {
    int elementCount = 5;
    GCPProject project = getSampleProject("");
    List<GCPProject> projects = new ArrayList<>(elementCount);

    for (int i = 0; i < elementCount; ++i) {
      projects.add(project);
    }

    GCPProject.setProjectsApiStub(this.projectsApiObject);
    when(this.getIamPolicy.execute()).thenReturn(getSamplePolicy(1));
    List<KV<GCPResource, GCPResourceState>> results = this.tester.processBatch(projects);
    assertEquals(results.size(), elementCount);
    for (int i = 0; i < elementCount; ++i) {
      assertEquals(results.get(i).getKey(), getSampleProject(""));
      assertEquals(results.get(i).getValue(), getSampleGCPResourcePolicy(project));
    }
  }

  private GCPProject getSampleProject(String suffix) {
    if (!suffix.equals("")) {
      suffix = "_" + suffix;
    }
    String projectId = "sampleProjectId" + suffix;
    String orgId = "sampleOrgId" + suffix;
    String projectName = "sampleProjectName" + suffix;
    return new GCPProject(projectId, orgId, projectName);
  }

  private GCPResourcePolicy getSampleGCPResourcePolicy(PoliciedObject resource) {
    List<String> members = Arrays.asList(MEMBER_1, MEMBER_2);
    PolicyBinding binding = new PolicyBinding(ROLE, members);
    return new GCPResourcePolicy(resource, Arrays.asList(binding));
  }

  private Policy getSamplePolicy(int bindingsCount) {
    Binding binding = new Binding().setRole(ROLE).setMembers(Arrays.asList(MEMBER_1, MEMBER_2));
    List<Binding> bindings = new ArrayList<>(bindingsCount);
    for (int i = 0; i < bindingsCount; ++i) {
      bindings.add(binding);
    }
    return new Policy().setBindings(bindings);
  }
}
