pipeline {
    agent {
        node {
            label 'della_rk9481'
        }
    }

    stages {

        stage (' Allocate resources '){
            steps {
                sh """
                    salloc -J jenkins_cpu_${env.GIT_COMMIT} -N 1 -n 1 -t 00:10:00 --constraint=broadwell &
                    salloc -J jenkins_gpu_${env.GIT_COMMIT} -N 1 -c 10 -t 00:10:00 --gres=gpu:1 --constraint=a100 &
                """
            }
        }

        stage (' Build and run PR branch '){

            stages {
                stage (' Update and Build'){
                    stages {
                        stage (' Update git modules '){
                            steps {
                                echo ' Getting git submodules '
                                sh 'git submodule init'
                                sh 'git submodule update'
                            }
                        }
                        stage (' Build '){
                            parallel{
                                stage (' Build CPU '){
                                    steps {
                                        echo " Building SPECFEM "
                                        sh """
                                            cmake -S . -B build_cpu -DCMAKE_BUILD_TYPE=Release
                                            cmake3 --build build_cpu
                                        """
                                    }
                                }

                                stage (' Build GPU '){
                                    steps {
                                        echo " Building SPECFEM "
                                        sh """
                                            module load cudatoolkit/11.7
                                            cmake -S . -B build_gpu -DCMAKE_BUILD_TYPE=Release -DKokkos_ENABLE_CUDA=ON -DKokkos_ARCH_AMPERE80=ON -DKokkos_ENABLE_OPENMP=ON
                                            cmake3 --build build_gpu
                                        """
                                    }
                                }
                            }
                        }
                    }
                })

                stage (' Run regression tests '){
                    parallel {
                        stage ('Run CPU tests'){
                            stages {
                                stage (' Check Allocations '){
                                    steps {
                                        sh """
                                            JOB_ID=$(squeue --format="%.i %.j" | grep "jenkins_cpu_${env.GIT_COMMIT}" | cut -d ' ' -f1)
                                            until srun --jobid=${JOB_ID} bash -c "echo Hello" &> /dev/null ; do sleep 30 ; done ;
                                        """
                                    }
                                }

                                stage (' Run test '){
                                    steps {
                                        sh """
                                            mkdir -p regression-tests/results
                                            JOB_ID=$(squeue --format="%.i %.j" | grep "jenkins_cpu_${env.GIT_COMMIT}" | cut -d ' ' -f1)
                                            srun --jobid=${JOB_ID} bash tests/regression-tests/run.sh -d cpu -i tests/regression-tests -e build_cpu/specfem2d -r regression-tests/results/PR-cpu.yaml
                                        """
                                    }
                                }
                            }
                        }

                        stage ('Run GPU tests'){
                            stages {
                                stage (' Check Allocations '){
                                    steps {
                                        sh """
                                            JOB_ID=$(squeue --format="%.i %.j" | grep "jenkins_gpu_${env.GIT_COMMIT}" | cut -d ' ' -f1)
                                            until srun --jobid=${JOB_ID} bash -c "echo Hello" &> /dev/null ; do sleep 30 ; done ;
                                        """
                                    }
                                }

                                stage (' Run test '){
                                    steps {
                                        sh """
                                            JOB_ID=$(squeue --format="%.i %.j" | grep "jenkins_gpu_${env.GIT_COMMIT}" | cut -d ' ' -f1)
                                            mkdir -p regression-tests/results
                                            srun --jobid=${JOB_ID} bash tests/regression-tests/run.sh -d gpu -i tests/regression-tests -e build_gpu/specfem2d -r regression-tests/results/PR-gpu.yaml
                                        """
                                    }
                                }
                            }
                        }
                    }
                }

                stage ( ' clean '){
                    steps {
                        sh """
                            rm -rf build_cpu
                            rm -rf build_gpu
                        """
                    }
                }
            }
        }

        stage (' Build and Test main branch '){
            stages {
                stage (' Checkout main branch '){
                    steps {
                        checkout([$class: 'GitSCM',
                                branches: [[name: 'regression-testing']],
                                extensions: [lfs()],
                                userRemoteConfigs: [[url: 'https://github.com/PrincetonUniversity/specfem2d_kokkos']]])
                    }
                }

                stage (' Update and Build '){
                    stages {
                        stage (' Update git modules '){
                            steps {
                                echo ' Getting git submodules '
                                sh 'git submodule init'
                                sh 'git submodule update'
                            }
                        }

                        stage (' Build '){
                            parallel{
                                stage (' Build CPU '){
                                    steps {
                                        echo " Building SPECFEM "
                                        sh """
                                            cmake -S . -B build_cpu -DCMAKE_BUILD_TYPE=Release
                                            cmake3 --build build_cpu
                                        """
                                    }
                                }

                                stage (' Build GPU '){
                                    steps {
                                        echo " Building SPECFEM "
                                        sh """
                                            module load cudatoolkit/11.7
                                            cmake -S . -B build_gpu -DCMAKE_BUILD_TYPE=Release -DKokkos_ENABLE_CUDA=ON -DKokkos_ARCH_AMPERE80=ON -DKokkos_ENABLE_OPENMP=ON
                                            cmake3 --build build_gpu
                                        """
                                    }
                                }
                            }
                        }
                    }
                }

                stage (' Run regression tests '){
                    parallel {
                        stage ('Run CPU tests'){
                            steps {
                                sh """
                                    mkdir -p regression-tests/results
                                    JOB_ID=$(squeue --format="%.i %.j" | grep "jenkins_cpu_${env.GIT_COMMIT}" | cut -d ' ' -f1)
                                    srun --jobid=${JOB_ID} bash tests/regression-tests/run.sh -d cpu -i tests/regression-tests -e build_cpu/specfem2d -r regression-tests/results/PR-cpu.yaml
                                """
                            }
                        }

                        stage ('Run GPU tests'){
                            steps {
                                sh """
                                    mkdir -p regression-tests/results
                                    JOB_ID=$(squeue --format="%.i %.j" | grep "jenkins_gpu_${env.GIT_COMMIT}" | cut -d ' ' -f1)
                                    srun --jobid=${JOB_ID} bash tests/regression-tests/run.sh -d gpu -i tests/regression-tests -e build_gpu/specfem2d -r regression-tests/results/PR-gpu.yaml
                                """
                            }
                        }
                    }
                }
            }
        }

        stage (' Compare results '){
            parallel {
                stage (' Compare CPU results '){
                    steps {
                        sh "./build_cpu/tests/regression-tests/compare_regression_tests --pr regression-results/results/PR-cpu.yaml --main regression-results/results/main-cpu.yaml"
                    }
                }
                stage (' Compare GPU results '){
                    steps {
                        sh "./build_gpu/tests/regression-tests/compare_regression_tests --pr regression-results/results/PR-cpu.yaml --main regression-results/results/main-cpu.yaml"
                    }
                }
            }
        }

        stage (' Clean '){
            steps {
                sh """
                    scancel --me
                    rm -rf build_cpu
                    rm -rf build_gpu
                """
            }
        }
    }
}
