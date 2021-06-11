{ pkgs ? import <nixpkgs> {} }:

with pkgs;
mkShell {
  nativeBuildInputs = with pkgs; [ cmake clang_11 ];

  #JAVA_HOME="/run/current-system/sw/lib/openjdk";
  LD_LIBRARY_PATH="${with xorg; lib.makeLibraryPath [ libXxf86vm ]}";
  JAVA_HOME="${jdk8}/lib/openjdk";

  shellHook = ''
    CC=${pkgs.clang_11}/bin/cc
    CXX=${pkgs.clang_11}/bin/c++
  '';
}
