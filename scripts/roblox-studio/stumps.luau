--[[
	Two cut stumps, each its own model at the top level so they move and delete
	independently of the camp and of each other.

	Each is a wide cylinder plus a thin lighter disc for the cut face, which is
	what reads as "sawn" rather than "post". PrimaryPart is the trunk, so the
	pivot sits on the ground.

	STUMPS is the whole layout: position, height, diameter, lean.
]]

local STUMPS = {
	{name = "Stump1", at = Vector3.new(9, 0.5, 3),  h = 2.0, d = 3.2, lean = 4},
	{name = "Stump2", at = Vector3.new(6, 0.5, 8),  h = 1.6, d = 2.6, lean = -6},
}

local function piece(parent, name, size, cf, colour)
	local p = Instance.new("Part")
	p.Name, p.Shape, p.Anchored, p.Size, p.CFrame = name, Enum.PartType.Cylinder, true, size, cf
	p.Color, p.Material = colour, Enum.Material.Wood
	p.TopSurface, p.BottomSurface = Enum.SurfaceType.Smooth, Enum.SurfaceType.Smooth
	p.Parent = parent
	return p
end

for _, s in ipairs(STUMPS) do
	local stump = Instance.new("Model")
	stump.Name, stump.Parent = s.name, workspace

	-- upright: cylinders are built along X, so roll them 90 degrees onto Y
	local upright = CFrame.Angles(0, 0, math.rad(90))
		* CFrame.Angles(math.rad(s.lean), 0, math.rad(s.lean * 0.5))

	local trunk = piece(stump, "Trunk", Vector3.new(s.h, s.d, s.d),
		CFrame.new(s.at + Vector3.new(0, s.h / 2, 0)) * upright,
		Color3.fromRGB(74, 57, 44))

	-- the cut face, a shade lighter and a touch narrower
	piece(stump, "Top", Vector3.new(0.12, s.d * 0.96, s.d * 0.96),
		CFrame.new(s.at + Vector3.new(0, s.h + 0.05, 0)) * upright,
		Color3.fromRGB(122, 98, 74))

	stump.PrimaryPart = trunk
end
