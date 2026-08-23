package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeRoleDto;
import za.co.titandynamix.sasbo_online_backend.dto.representative.UnionStructureDto;

import java.util.List;
import java.util.Optional;

public interface RepresentativeService {
    List<RepresentativeDto> findAll();
    Optional<RepresentativeDto> findById(Integer id);
    RepresentativeDto create(RepresentativeDto dto);
    Optional<RepresentativeDto> update(Integer id, RepresentativeDto dto);
    boolean delete(Integer id);

    List<RepresentativeRoleDto> findAllRepresentativeRoles();

    List<UnionStructureDto> findAllStructures();

    List<UnionStructureDto> findStructuresTree();
}
