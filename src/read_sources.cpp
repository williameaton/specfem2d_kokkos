#include "../include/read_sources.h"
#include "../include/config.h"
#include "../include/source.h"
#include "../include/utils.h"
#include "yaml-cpp/yaml.h"
#include <vector>

void operator>>(YAML::Node &Node,
                specfem::utilities::force_source &force_source) {
  force_source.x = Node["x"].as<type_real>();
  force_source.z = Node["z"].as<type_real>();
  force_source.source_surf = Node["source_surf"].as<bool>();
  force_source.stf_type = Node["stf_type"].as<std::string>();
  force_source.f0 = Node["f0"].as<type_real>();
  force_source.angle = Node["angle"].as<type_real>();
  force_source.vx = Node["vx"].as<type_real>();
  force_source.vz = Node["vz"].as<type_real>();
  force_source.factor = Node["factor"].as<type_real>();
}

void operator>>(YAML::Node &Node,
                specfem::utilities::moment_tensor &moment_tensor) {
  moment_tensor.x = Node["x"].as<type_real>();
  moment_tensor.z = Node["z"].as<type_real>();
  moment_tensor.source_surf = Node["source_surf"].as<bool>();
  moment_tensor.stf_type = Node["stf_type"].as<std::string>();
  moment_tensor.f0 = Node["f0"].as<type_real>();
  moment_tensor.Mxx = Node["Mxx"].as<type_real>();
  moment_tensor.Mxz = Node["Mxz"].as<type_real>();
  moment_tensor.Mzz = Node["Mzz"].as<type_real>();
  moment_tensor.vx = Node["vx"].as<type_real>();
  moment_tensor.vz = Node["vz"].as<type_real>();
  moment_tensor.factor = Node["factor"].as<type_real>();
}

std::vector<specfem::sources::source *>
specfem::read_sources(const std::string sources_file,
                      const specfem::MPI::MPI *mpi) {
  // read sources file
  std::vector<specfem::sources::source *> sources;
  YAML::Node yaml = YAML::LoadFile(sources_file);
  int nsources = yaml["number-of-sources"].as<int>();
  YAML::Node Node = yaml["sources"];
  assert(Node.IsSequence());
  for (auto N : Node) {
    if (N["source_type"].as<std::string>() == "force source") {
      specfem::utilities::force_source force_source;
      N >> force_source;
      sources.push_back(new specfem::sources::force(force_source, wave));
    }

    if (N["source_type"].as<std::string>() == "Moment-tensor source") {
      specfem::utilities::moment_tensor moment_tensor;
      N >> moment_tensor;
      sources.push_back(new specfem::sources::moment_tensor(moment_tensor));
    }
  }

  if (sources.size() != nsources) {
    std::ostringstream message;
    message << "Found only " << sources.size()
            << " number of sources. Total number of sources in " << sources_file
            << " are" << nsources
            << " Please check if there is a error in sources file.";
    throw std::runtime_error(message.str());
  }
  // Dummy return type. Should never reach here.
  return sources;
}
