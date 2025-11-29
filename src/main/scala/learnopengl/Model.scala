package learnopengl.model

import learnopengl.mesh.*
import learnopengl.shader.Shader
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.assimp.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

import java.nio.ByteBuffer
import scala.collection.mutable.ArrayBuffer

class Model(path: String, val gammaCorrection: Boolean = false):

  // all loaded textures to avoid duplicates
  val texturesLoaded = ArrayBuffer[Texture]()
  val meshes = ArrayBuffer[Mesh]()
  var directory: String = _

  // load model immediately
  loadModel(path)

  // draw all meshes
  def draw(shader: Shader): Unit =
    // println(s"drawing ${meshes.length} meshes")
    for m <- meshes do m.draw(shader)

  // ------------------------
  // PRIVATE IMPLEMENTATION
  // ------------------------

  private def loadModel(path: String): Unit =
    val scene = Assimp.aiImportFile(
      path,
      Assimp.aiProcess_Triangulate |
        Assimp.aiProcess_GenSmoothNormals |
        Assimp.aiProcess_FlipUVs |
        Assimp.aiProcess_CalcTangentSpace
    )

    if scene == null || scene.address() == 0L then
      throw new RuntimeException("ASSIMP error: " + Assimp.aiGetErrorString())

    // retrieve the directory path of the filepath
    directory = path.substring(0, path.lastIndexOf('/'))

    // process ASSIMP's root node recursively
    processNode(scene.mRootNode(), scene)

  private def processNode(node: AINode, scene: AIScene): Unit =
    // process each mesh located at the current node
    for i <- 0 until node.mNumMeshes() do
      // the node object only contains indices to index the actual objects in the scene.
      // the scene contains all the data, node is just to keep stuff organized (like relations between nodes).
      val meshIdx = node.mMeshes().get(i)
      val aim = AIMesh.create(scene.mMeshes().get(meshIdx))
      meshes += processMesh(aim, scene)

    // after we've processed all of the meshes (if any) we then recursively process each of the children nodes
    for i <- 0 until node.mNumChildren() do
      processNode(AINode.create(node.mChildren().get(i)), scene)

  private def processMesh(mesh: AIMesh, scene: AIScene): Mesh =
    // data to fill
    val vertices = ArrayBuffer[Vertex]()
    val indices = ArrayBuffer[Int]()
    val textures = ArrayBuffer[Texture]()

    // walk through each of the mesh's vertices
    for i <- 0 until mesh.mNumVertices() do
      val pos = mesh.mVertices().get(i)
      val vector = Vector3f(pos.x, pos.y, pos.z)
      val vertex = Vertex(
        Position = vector,
        Normal = if mesh.mNormals != null then
          val n = mesh.mNormals.get(i)
          Vector3f(n.x, n.y, n.z)
        else Vector3f(),
        TexCoords = if mesh.mTextureCoords(0) != null then
          val tc = mesh.mTextureCoords(0)
          Vector2f(tc.x, tc.y)
        else Vector2f(0f, 0f),
        Tangent = if mesh.mTangents != null then
          val t = mesh.mTangents.get(i)
          Vector3f(t.x, t.y, t.z)
        else Vector3f(),
        Bitangent = if mesh.mBitangents != null then
          val b = mesh.mBitangents.get(i)
          Vector3f(b.x, b.y, b.z)
        else Vector3f()
      )
      vertices += vertex

    // now walk through each of the mesh's faces (a face is a mesh its triangle) and retrieve the corresponding vertex indices.
    for i <- 0 until mesh.mNumFaces() do
      val face = mesh.mFaces().get(i)
      // retrieve all indices of the face and store them in the indices vector
      for j <- 0 until face.mNumIndices do indices += face.mIndices.get(j)

    // process materials
    val material =
      AIMaterial.create(scene.mMaterials().get(mesh.mMaterialIndex()))

    // we assume a convention for sampler names in the shaders. Each diffuse texture should be named
    // as 'texture_diffuseN' where N is a sequential number ranging from 1 to MAX_SAMPLER_NUMBER.
    // Same applies to other texture as the following list summarizes:
    // diffuse: texture_diffuseN
    // specular: texture_specularN
    // normal: texture_normalN

    // 1. diffuse maps
    textures ++= loadMaterialTextures(
      material,
      Assimp.aiTextureType_DIFFUSE,
      "texture_diffuse"
    )
    // 2. specular maps
    textures ++= loadMaterialTextures(
      material,
      Assimp.aiTextureType_SPECULAR,
      "texture_specular"
    )
    // 3. normal maps
    textures ++= loadMaterialTextures(
      material,
      Assimp.aiTextureType_HEIGHT,
      "texture_normal"
    )
    // 4. height maps
    textures ++= loadMaterialTextures(
      material,
      Assimp.aiTextureType_AMBIENT,
      "texture_height"
    )

    // return a mesh object created from the extracted mesh data
    Mesh(vertices.toSeq, indices.toSeq, textures.toSeq)

  // checks all material textures of a given type and loads the textures if they're not loaded yet.
  // the required info is returned as a Texture struct.
  private def loadMaterialTextures(
      mat: AIMaterial,
      tpe: Int,
      typeName: String
  ): Seq[Texture] =
    val textures = ArrayBuffer[Texture]()

    for i <- 0 until Assimp.aiGetMaterialTextureCount(mat, tpe) do
      val str = getTexturePath(mat, tpe, i)

      // check if texture was loaded before and if so, continue to next iteration: skip loading a new texture
      texturesLoaded.find(_.path == str) match
        case Some(tex) =>
          // a texture with the same filepath has already been loaded, continue to next one. (optimization)
          textures += tex
        case None =>
          val tex = Texture(
            id = textureFromFile(str, directory),
            tpe = typeName,
            path = str
          )
          textures += tex
          // store it as texture loaded for entire model, to ensure we won't unnecessary load duplicate textures.
          texturesLoaded += tex

      // path.free()

    textures.toSeq

// In LWJGL 3.x, there is no simple 4-argument aiGetMaterialTexture in the Java bindings. That overload exists
// in C++, but LWJGL exposes only the “full” overload with 10 parameters (or buffers). So trying to call it with
// just AIString fails.
def getTexturePath(mat: AIMaterial, tpe: Int, index: Int): String =
  val path = AIString.calloc()

  val dummyI = MemoryUtil.memAllocInt(1)
  val dummyF = MemoryUtil.memAllocFloat(1)

  val res = Assimp.aiGetMaterialTexture(
    mat,
    tpe,
    index,
    path,
    dummyI, // mapping
    dummyI, // uvIndex
    dummyF, // blend
    dummyI, // op
    dummyI, // mapMode
    null // texFlags
  )

  val result =
    if res == Assimp.aiReturn_SUCCESS then path.dataString()
    else ""

  // free temp buffers
  path.free()
  MemoryUtil.memFree(dummyI)
  MemoryUtil.memFree(dummyF)

  result

// utility function for loading a 2D texture from file
// ---------------------------------------------------
def textureFromFile(path: String, directory: String): Int = {
  val filename = directory + '/' + path
  val textureID = glGenTextures()

  usingStack { stack =>
    val width = stack.mallocInt(1)
    val heigth = stack.mallocInt(1)
    val nrComponents = stack.mallocInt(1)

    // val imgBytes = loadResourceAsTexture(path)
    // val data = stbi_load_from_memory(imgBytes, w, h, nrComponents, 0)
    val data = stbi_load(filename, width, heigth, nrComponents, 0)
    if (data == null)
      throw new RuntimeException("Failed to load texture: " + filename)

    val format = nrComponents.get() match {
      case 1 => GL_RED
      case 3 => GL_RGB
      case 4 => GL_RGBA
    }

    glBindTexture(GL_TEXTURE_2D, textureID)
    glTexImage2D(
      GL_TEXTURE_2D,
      0,
      format,
      width.get(),
      heigth.get(),
      0,
      format,
      GL_UNSIGNED_BYTE,
      data
    )
    glGenerateMipmap(GL_TEXTURE_2D)

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
    glTexParameteri(
      GL_TEXTURE_2D,
      GL_TEXTURE_MIN_FILTER,
      GL_LINEAR_MIPMAP_LINEAR
    )
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

    stbi_image_free(data)
  }

  textureID
}

def usingStack[A](f: MemoryStack => A): A =
  val stack = MemoryStack.stackPush()
  try f(stack)
  finally stack.pop()
