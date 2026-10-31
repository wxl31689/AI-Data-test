package com.wxl.testdataagent.project;

import com.wxl.testdataagent.common.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        String description = request.description() == null ? null : request.description().trim();
        ProjectEntity entity = new ProjectEntity(request.name().trim(), description);
        return ProjectResponse.from(projectRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        return projectRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(String id) {
        return projectRepository.findById(id)
                .map(ProjectResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("项目不存在"));
    }
}

