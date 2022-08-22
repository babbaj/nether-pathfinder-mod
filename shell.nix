{ pkgs ? import <nixpkgs> {} }:

with pkgs;
mkShell {
  nativeBuildInputs = with pkgs; [ cmake clang_14 ];

  #JAVA_HOME="/run/current-system/sw/lib/openjdk";
  LD_LIBRARY_PATH="${with xorg; lib.makeLibraryPath [ libXxf86vm ]}";
  JAVA_HOME="${jdk8}/lib/openjdk";

  shellHook = ''
    CC=${pkgs.clang_14}/bin/cc
    CXX=${pkgs.clang_14}/bin/c++
  '';
}
